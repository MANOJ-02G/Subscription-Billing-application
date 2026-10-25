import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Core service: subscribes customers, creates invoices (with proration / discount codes),
 * records payments and produces revenue and aging reports.
 */
public class BillingService {

    /** Simple record of a received payment. */
    public static class Payment {
        private final String paymentId, invoiceNo, method;
        private final double amount;
        private final LocalDate date;

        Payment(String paymentId, String invoiceNo, double amount, String method, LocalDate date) {
            this.paymentId = paymentId; this.invoiceNo = invoiceNo;
            this.amount = amount; this.method = method; this.date = date;
        }
        public String getPaymentId() { return paymentId; }
        public String getInvoiceNo() { return invoiceNo; }
        public double getAmount()    { return amount; }
        public String getMethod()    { return method; }
        public LocalDate getDate()   { return date; }
    }

    private static int totalInvoicesGenerated = 0;
    private static int paymentCounter = 0;

    private final Map<String, Plan> plans = new LinkedHashMap<String, Plan>();
    private final Map<String, Subscriber> subscribers = new LinkedHashMap<String, Subscriber>();
    private final Map<String, Invoice> invoices = new LinkedHashMap<String, Invoice>();
    private final List<Payment> payments = new ArrayList<Payment>();
    private final Map<String, Double> discountCodes = new HashMap<String, Double>();
    private final int paymentTermDays;
    private final int suspendAfterOverdueDays;

    public BillingService(int paymentTermDays, int suspendAfterOverdueDays) {
        this.paymentTermDays = paymentTermDays;
        this.suspendAfterOverdueDays = suspendAfterOverdueDays;
    }

    // ------------------------------------------------------------------ setup
    public void addPlan(Plan plan)               { plans.put(plan.getPlanId(), plan); }
    public void addSubscriber(Subscriber s)      { subscribers.put(s.getId(), s); }
    public void addDiscountCode(String code, double percent) { discountCodes.put(code.toUpperCase(), percent); }
    public Plan findPlan(String planId)          { return plans.get(planId); }
    public Subscriber findSubscriber(String id)  { return subscribers.get(id); }
    public Invoice findInvoice(String invoiceNo) { return invoices.get(invoiceNo); }

    public List<Invoice> getInvoicesFor(String subscriberId) {
        List<Invoice> result = new ArrayList<Invoice>();
        for (Invoice inv : invoices.values()) {
            if (inv.getSubscriberId().equals(subscriberId)) result.add(inv);
        }
        return result;
    }

    // ------------------------------------------------------------ subscriptions
    public void subscribe(String subscriberId, String planId, LocalDate date) {
        Subscriber s = subscribers.get(subscriberId);
        Plan p = plans.get(planId);
        if (s == null || p == null) {
            System.out.println("  [ERROR] Unknown subscriber or plan");
            return;
        }
        s.subscribe(p, date);
        System.out.printf("  %s subscribed to %s on %s -> status %s%s%n", s.getName(), p.getName(), date, s.getStatus(),
                s.getStatus() == Subscriber.Status.TRIAL ? " (trial ends " + s.getTrialEndDate() + ")" : "");
    }

    /** Changes the plan and applies proration if the subscriber is mid-cycle. */
    public void changePlan(String subscriberId, String newPlanId, LocalDate changeDate) {
        Subscriber s = subscribers.get(subscriberId);
        Plan newPlan = plans.get(newPlanId);
        if (s == null || newPlan == null) {
            System.out.println("  [ERROR] Unknown subscriber or plan");
            return;
        }
        Plan oldPlan = s.getCurrentPlan();
        if (oldPlan != null && oldPlan.getPlanId().equals(newPlan.getPlanId())) {
            System.out.println("  [CHANGE REJECTED] " + s.getName() + " is already on plan " + newPlan.getName());
            return;
        }
        boolean midCycle = s.getStatus() == Subscriber.Status.ACTIVE
                && s.getCycleStart() != null && s.getNextBillingDate() != null
                && !changeDate.isBefore(s.getCycleStart()) && changeDate.isBefore(s.getNextBillingDate());

        double net = midCycle ? calculateProration(s, newPlan, changeDate) : 0.0;   // before switching plan
        try {
            s.changePlan(newPlan);
        } catch (RuntimeException e) {
            System.out.println("  [CHANGE REJECTED] " + e.getMessage());
            return;
        }
        System.out.printf("  %s changed plan %s -> %s on %s%n", s.getName(), oldPlan.getName(), newPlan.getName(), changeDate);

        if (!midCycle) {
            System.out.println("  No proration (not mid-cycle); new plan applies from next invoice.");
        } else if (net > 0) {
            generateInvoice(s, changeDate, net, "Proration: " + oldPlan.getName() + " -> " + newPlan.getName());
        } else if (net < 0) {
            s.addCredit(-net);
            System.out.printf("  Downgrade credit of %.2f stored for %s%n", -net, s.getName());
        }
    }

    /**
     * Proration for the rest of the current cycle:
     * (new plan's monthly-equivalent price x old cycle length - old plan's cycle price) x remaining/total days.
     * Positive = extra charge, negative = credit.
     */
    public double calculateProration(Subscriber s, Plan newPlan, LocalDate changeDate) {
        Plan oldPlan = s.getCurrentPlan();
        long totalDays = ChronoUnit.DAYS.between(s.getCycleStart(), s.getNextBillingDate());
        long remaining = ChronoUnit.DAYS.between(changeDate, s.getNextBillingDate());
        double fraction = (double) remaining / totalDays;

        double credit = oldPlan.computeAmount() * fraction;                                        // unused old plan
        double charge = newPlan.getEffectiveMonthlyPrice() * oldPlan.getBillingCycleMonths() * fraction; // new plan, same period
        double net = Plan.round2(charge - credit);
        System.out.printf("  Proration: %d of %d days left | credit for %s %.2f | charge for %s %.2f | net %.2f%n",
                remaining, totalDays, oldPlan.getName(), credit, newPlan.getName(), charge, net);
        return net;
    }

    // --------------------------------------------------- invoice generation (overloaded)
    /** Regular invoice, no discount. */
    public Invoice generateInvoice(Subscriber s, LocalDate issueDate) {
        return generateInvoice(s, issueDate, (String) null);
    }

    /** Regular invoice with an optional discount code. */
    public Invoice generateInvoice(Subscriber s, LocalDate issueDate, String discountCode) {
        Plan plan = s.getCurrentPlan();
        if (plan == null || s.getStatus() == Subscriber.Status.CANCELLED || s.getStatus() == Subscriber.Status.SUSPENDED
                || s.getStatus() == Subscriber.Status.NEW) {
            System.out.println("  [SKIP] " + s.getName() + " cannot be billed (status " + s.getStatus() + ")");
            return null;
        }
        if (s.getStatus() == Subscriber.Status.TRIAL) {
            if (issueDate.isBefore(s.getTrialEndDate())) {
                System.out.println("  [SKIP] " + s.getName() + " is still in trial until " + s.getTrialEndDate());
                return null;
            }
            s.activateAfterTrial();
        }

        double base = plan.computeAmount();                // polymorphic call: Monthly/Annual decide
        String note = plan.getName() + " (" + plan.getPlanType() + ")";
        double discounted = base;
        if (discountCode != null) {
            Double pct = discountCodes.get(discountCode.toUpperCase());
            if (pct == null) {
                System.out.println("  [WARN] Invalid discount code '" + discountCode + "' ignored");
            } else {
                discounted = Plan.round2(base * (1 - pct / 100.0));
                note += " less " + pct + "% (" + discountCode.toUpperCase() + ")";
            }
        }
        double creditUsed = s.useCredit(discounted);
        if (creditUsed > 0) note += ", credit applied " + String.format("%.2f", creditUsed);

        Invoice inv = createInvoice(s, plan, discounted - creditUsed, issueDate, note);
        s.advanceCycle(issueDate, plan.getBillingCycleMonths());
        return inv;
    }

    /** Invoice for an explicit amount (used for proration charges). Does not move the billing cycle. */
    public Invoice generateInvoice(Subscriber s, LocalDate issueDate, double amount, String description) {
        return createInvoice(s, s.getCurrentPlan(), amount, issueDate, description);
    }

    private Invoice createInvoice(Subscriber s, Plan plan, double amount, LocalDate issueDate, String description) {
        Invoice inv = new Invoice(s.getId(), plan.getPlanId(), amount, issueDate,
                issueDate.plusDays(paymentTermDays), description);
        invoices.put(inv.getInvoiceNo(), inv);
        totalInvoicesGenerated++;
        System.out.println("  Invoice created: " + inv);
        return inv;
    }

    /** Bills everyone whose next billing date has arrived. */
    public void runBillingCycle(LocalDate date) {
        System.out.println("  -- Billing run for " + date + " --");
        boolean any = false;
        for (Subscriber s : subscribers.values()) {
            boolean due = s.getNextBillingDate() != null && !date.isBefore(s.getNextBillingDate())
                    && (s.getStatus() == Subscriber.Status.ACTIVE || s.getStatus() == Subscriber.Status.TRIAL);
            if (due) { generateInvoice(s, date); any = true; }
        }
        if (!any) System.out.println("  Nobody is due for billing.");
    }

    // ----------------------------------------------------------------- payments
    public boolean recordPayment(String invoiceNo, double amount, String method, LocalDate date) {
        Invoice inv = invoices.get(invoiceNo);
        if (inv == null) {
            System.out.println("  [ERROR] No such invoice " + invoiceNo);
            return false;
        }
        try {
            inv.applyPayment(amount);
        } catch (RuntimeException e) {
            System.out.println("  [PAYMENT REJECTED] " + e.getMessage());
            return false;
        }
        paymentCounter++;
        payments.add(new Payment(String.format("PAY-%04d", paymentCounter), invoiceNo, amount, method, date));
        System.out.printf("  Payment %.2f (%s) on %s -> %s is now %s, balance %.2f%n",
                amount, method, date, invoiceNo, inv.getState(), inv.getBalance());

        Subscriber s = subscribers.get(inv.getSubscriberId());
        if (s != null && s.getStatus() == Subscriber.Status.SUSPENDED && !hasPastDue(s.getId(), date)) {
            s.reactivate();
            System.out.println("  " + s.getName() + " has no overdue invoices -> account REACTIVATED");
        }
        return true;
    }

    private boolean hasPastDue(String subscriberId, LocalDate asOf) {
        for (Invoice inv : invoices.values()) {
            if (inv.getSubscriberId().equals(subscriberId) && inv.isPastDue(asOf)) return true;
        }
        return false;
    }

    // ------------------------------------------------------------------ overdue
    public void runOverdueCheck(LocalDate asOf) {
        System.out.println("  -- Overdue check as of " + asOf + " --");
        for (Invoice inv : invoices.values()) {
            if (inv.markOverdue(asOf)) {
                System.out.printf("  %s marked OVERDUE (%d days late)%n", inv.getInvoiceNo(), inv.daysOverdue(asOf));
            }
        }
        for (Subscriber s : subscribers.values()) {
            if (s.getStatus() != Subscriber.Status.ACTIVE) continue;
            for (Invoice inv : getInvoicesFor(s.getId())) {
                if (inv.daysOverdue(asOf) > suspendAfterOverdueDays) {
                    s.suspend();
                    System.out.printf("  %s SUSPENDED: %s is %d days overdue%n", s.getName(), inv.getInvoiceNo(), inv.daysOverdue(asOf));
                    break;
                }
            }
        }
    }

    // ------------------------------------------------------------------ reports
    public void printSubscribers() {
        for (Subscriber s : subscribers.values()) System.out.println("  " + s);
    }

    public void printInvoiceRegister() {
        for (Invoice inv : invoices.values()) System.out.println("  " + inv);
    }

    public void printRevenueReport() {
        double invoiced = 0, collected = 0;
        Map<String, Double> byPlan = new TreeMap<String, Double>();
        Map<String, Double> byMethod = new TreeMap<String, Double>();
        Map<Invoice.State, Integer> byState = new TreeMap<Invoice.State, Integer>();
        for (Invoice inv : invoices.values()) {
            byState.merge(inv.getState(), 1, Integer::sum);
            if (inv.getState() == Invoice.State.CANCELLED) continue;
            invoiced += inv.getAmount();
            collected += inv.getAmountPaid();
            byPlan.merge(inv.getPlanId(), inv.getAmountPaid(), Double::sum);
        }
        for (Payment p : payments) byMethod.merge(p.getMethod(), p.getAmount(), Double::sum);

        System.out.println("  ===== REVENUE REPORT =====");
        System.out.printf("  Invoices issued : %d%n", totalInvoicesGenerated);
        System.out.printf("  Total invoiced  : %10.2f%n", invoiced);
        System.out.printf("  Total collected : %10.2f%n", collected);
        System.out.printf("  Outstanding     : %10.2f%n", invoiced - collected);
        System.out.printf("  Collection rate : %9.1f%%%n", invoiced == 0 ? 0 : 100 * collected / invoiced);
        System.out.println("  Collected by plan:");
        for (Map.Entry<String, Double> e : byPlan.entrySet()) {
            Plan p = plans.get(e.getKey());
            System.out.printf("    %-8s %10.2f%n", p == null ? e.getKey() : p.getName(), e.getValue());
        }
        System.out.println("  Collected by method:");
        for (Map.Entry<String, Double> e : byMethod.entrySet()) {
            System.out.printf("    %-8s %10.2f%n", e.getKey(), e.getValue());
        }
        System.out.println("  Invoices by state: " + byState);
    }

    public void printAgingReport(LocalDate asOf) {
        String[] labels = {"1-30 days", "31-60 days", "61-90 days", "90+ days"};
        double[] totals = new double[4];
        int[] counts = new int[4];

        System.out.println("  ===== AGING REPORT as of " + asOf + " =====");
        for (Invoice inv : invoices.values()) {
            long d = inv.daysOverdue(asOf);
            if (d <= 0) continue;
            int b = d <= 30 ? 0 : d <= 60 ? 1 : d <= 90 ? 2 : 3;
            totals[b] += inv.getBalance();
            counts[b]++;
            Subscriber s = subscribers.get(inv.getSubscriberId());
            System.out.printf("  %-10s %s %-8s balance %9.2f due %s (%d days overdue)%n",
                    labels[b], inv.getInvoiceNo(), s == null ? "?" : s.getName(), inv.getBalance(), inv.getDueDate(), d);
        }
        double grand = 0;
        System.out.println("  Bucket summary:");
        for (int i = 0; i < 4; i++) {
            System.out.printf("    %-10s : %d invoice(s), %9.2f%n", labels[i], counts[i], totals[i]);
            grand += totals[i];
        }
        System.out.printf("    %-10s : %9.2f%n", "TOTAL", grand);
    }

    public static int getTotalInvoicesGenerated() { return totalInvoicesGenerated; }
}
