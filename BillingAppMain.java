import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class BillingAppMain {

    private static void section(String title) {
        System.out.println();
        System.out.println("=== " + title + " ===");
    }

    private static Invoice last(List<Invoice> list) {
        return list.get(list.size() - 1);
    }

    public static void main(String[] args) {
        BillingService billing = new BillingService(15, 30);   // 15-day terms, suspend after 30 days overdue
        billing.addDiscountCode("WELCOME10", 10);
        billing.addDiscountCode("SAVE20", 20);

        // ------------------------------------------------------------ 1. plans
        section("1. Create plans (stored as Plan references)");
        List<Plan> catalogue = new ArrayList<Plan>();
        catalogue.add(new MonthlyPlan("P-BASIC", "Basic", 299.0,
                Arrays.asList("1 user", "5 GB storage", "Email support"), 0));
        catalogue.add(new MonthlyPlan("P-PRO", "Pro", 799.0,
                Arrays.asList("10 users", "100 GB storage", "Priority support"), 7));
        catalogue.add(new AnnualPlan("P-PREM", "Premium", 1499.0,
                Arrays.asList("Unlimited users", "1 TB storage", "24x7 support"), 14, 15.0));
        for (Plan p : catalogue) {
            billing.addPlan(p);
            // polymorphism: the right computeAmount() runs for each plan type
            System.out.printf("  %s%n    -> charge per cycle (%s): %.2f%n", p, p.getClass().getSimpleName(), p.computeAmount());
        }
        System.out.println("  Plans created: " + Plan.getPlanCount());

        // -------------------------------------------------------- 2. subscribers
        section("2. Create subscribers and subscribe");
        billing.addSubscriber(new Subscriber("S001", "Asha",    "asha@example.com"));
        billing.addSubscriber(new Subscriber("S002", "Ravi",    "ravi@example.com"));
        billing.addSubscriber(new Subscriber("S003", "Meena",   "meena@example.com"));
        billing.addSubscriber(new Subscriber("S004", "Karthik", "karthik@example.com"));

        LocalDate jan1 = LocalDate.of(2026, 1, 1);
        billing.subscribe("S001", "P-PRO", jan1);       // 7-day trial
        billing.subscribe("S002", "P-BASIC", jan1);     // no trial
        billing.subscribe("S003", "P-PREM", jan1);      // 14-day trial, annual
        billing.subscribe("S004", "P-BASIC", jan1);

        // encapsulation: invalid operations are rejected by the object itself
        try {
            billing.findSubscriber("S002").subscribe(billing.findPlan("P-PRO"), jan1);
        } catch (IllegalStateException e) {
            System.out.println("  [Encapsulation] " + e.getMessage());
        }

        // -------------------------------------------------- 3. first invoices
        section("3. Generate invoices on 2026-01-01 (overloaded generateInvoice)");
        System.out.println("  With a discount code (Karthik):");
        billing.generateInvoice(billing.findSubscriber("S004"), jan1, "WELCOME10");
        System.out.println("  Billing run for everyone else due today (no discount code):");
        billing.runBillingCycle(jan1);                  // Ravi (Asha & Meena are in trial)

        section("4. Payments");
        billing.recordPayment(last(billing.getInvoicesFor("S002")).getInvoiceNo(), 299.0, "UPI", LocalDate.of(2026, 1, 5));

        // ----------------------------------------------------- trials end
        section("5. Trial ends: Asha (Jan 8) and Meena (Jan 15)");
        billing.runBillingCycle(LocalDate.of(2026, 1, 8));
        billing.recordPayment(last(billing.getInvoicesFor("S001")).getInvoiceNo(), 799.0, "CARD", LocalDate.of(2026, 1, 10));
        billing.runBillingCycle(LocalDate.of(2026, 1, 15));
        Invoice meenaInv = last(billing.getInvoicesFor("S003"));
        billing.recordPayment(meenaInv.getInvoiceNo(), 10000.0, "BANK", LocalDate.of(2026, 1, 20));   // partial
        System.out.println("  Try to overpay (rejected by Invoice):");
        billing.recordPayment(meenaInv.getInvoiceNo(), 999999.0, "BANK", LocalDate.of(2026, 1, 21));

        // ------------------------------------------------------ plan changes
        section("6. Change plans with proration");
        System.out.println("  Ravi upgrades Basic -> Pro on Jan 16:");
        billing.changePlan("S002", "P-PRO", LocalDate.of(2026, 1, 16));
        billing.recordPayment(last(billing.getInvoicesFor("S002")).getInvoiceNo(),
                last(billing.getInvoicesFor("S002")).getBalance(), "UPI", LocalDate.of(2026, 1, 18));
        System.out.println("  Asha downgrades Pro -> Basic on Jan 20 (generates a credit):");
        billing.changePlan("S001", "P-BASIC", LocalDate.of(2026, 1, 20));
        System.out.println("  Asha tries to 'change' to the plan she already has:");
        billing.changePlan("S001", "P-BASIC", LocalDate.of(2026, 1, 21));

        // -------------------------------------------------------- next cycles
        section("7. Next billing cycles (Feb 1 and Feb 8)");
        billing.runBillingCycle(LocalDate.of(2026, 2, 1));
        billing.runBillingCycle(LocalDate.of(2026, 2, 8));    // Asha's credit covers her invoice

        // -------------------------------------------------------------- overdue
        LocalDate mar15 = LocalDate.of(2026, 3, 15);
        section("8. Overdue check and aging report (" + mar15 + ")");
        billing.runOverdueCheck(mar15);
        billing.printAgingReport(mar15);

        section("9. Subscribers and invoice register");
        billing.printSubscribers();
        System.out.println();
        billing.printInvoiceRegister();

        section("10. Karthik pays everything on Mar 16 -> reactivated");
        LocalDate mar16 = LocalDate.of(2026, 3, 16);
        for (Invoice inv : billing.getInvoicesFor("S004")) {
            if (inv.isOpen()) billing.recordPayment(inv.getInvoiceNo(), inv.getBalance(), "CARD", mar16);
        }

        section("11. Revenue report");
        billing.printRevenueReport();
        System.out.println();
        billing.printAgingReport(mar16);
    }
}
