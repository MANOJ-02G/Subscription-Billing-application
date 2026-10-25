import java.time.LocalDate;

/**
 * A customer. The status and billing dates are private and only change through
 * subscribe(), changePlan(), suspend(), reactivate(), cancel() ... (encapsulation).
 */
public class Subscriber {

    public enum Status { NEW, TRIAL, ACTIVE, SUSPENDED, CANCELLED }

    private static int subscriberCount = 0;

    private final String id;
    private String name;
    private String email;
    private Plan currentPlan;
    private Status status;

    private LocalDate startDate;
    private LocalDate trialEndDate;
    private LocalDate cycleStart;
    private LocalDate nextBillingDate;
    private double creditBalance;

    public Subscriber(String id, String name, String email) {
        this.id = id;
        this.name = name;
        setEmail(email);
        this.status = Status.NEW;
        this.creditBalance = 0.0;
        subscriberCount++;
    }

    // ---- lifecycle ----
    public void subscribe(Plan plan, LocalDate start) {
        if (plan == null) throw new IllegalArgumentException("Plan is required");
        if (status != Status.NEW && status != Status.CANCELLED) {
            throw new IllegalStateException(name + " already has a subscription (" + status + ")");
        }
        this.currentPlan = plan;
        this.startDate = start;
        if (plan.getTrialDays() > 0) {
            this.status = Status.TRIAL;
            this.trialEndDate = start.plusDays(plan.getTrialDays());
            this.cycleStart = trialEndDate;
            this.nextBillingDate = trialEndDate;      // first invoice when trial ends
        } else {
            this.status = Status.ACTIVE;
            this.trialEndDate = null;
            this.cycleStart = start;
            this.nextBillingDate = start;             // bill immediately
        }
    }

    public void changePlan(Plan newPlan) {
        if (newPlan == null) throw new IllegalArgumentException("New plan is required");
        if (status == Status.NEW || status == Status.CANCELLED) {
            throw new IllegalStateException("Cannot change plan while status is " + status);
        }
        if (currentPlan != null && currentPlan.getPlanId().equals(newPlan.getPlanId())) {
            throw new IllegalStateException("Already on plan " + newPlan.getName());
        }
        this.currentPlan = newPlan;
    }

    public void activateAfterTrial() {
        if (status != Status.TRIAL) throw new IllegalStateException("Not in trial");
        status = Status.ACTIVE;
    }

    public void suspend() {
        if (status != Status.ACTIVE && status != Status.TRIAL) {
            throw new IllegalStateException("Only active/trial subscribers can be suspended");
        }
        status = Status.SUSPENDED;
    }

    public void reactivate() {
        if (status != Status.SUSPENDED) throw new IllegalStateException("Subscriber is not suspended");
        status = Status.ACTIVE;
    }

    public void cancel() {
        if (status == Status.NEW) throw new IllegalStateException("Nothing to cancel");
        status = Status.CANCELLED;
        nextBillingDate = null;
    }

    // ---- billing helpers (called by BillingService) ----
    public void advanceCycle(LocalDate billedOn, int months) {
        this.cycleStart = billedOn;
        this.nextBillingDate = billedOn.plusMonths(months);
    }

    public void addCredit(double amount) {
        if (amount > 0) creditBalance = Plan.round2(creditBalance + amount);
    }

    /** Uses up to maxAmount of stored credit; returns the amount actually used. */
    public double useCredit(double maxAmount) {
        double used = Math.min(creditBalance, Math.max(0, maxAmount));
        creditBalance = Plan.round2(creditBalance - used);
        return Plan.round2(used);
    }

    // ---- getters / setters ----
    public String getId()                 { return id; }
    public String getName()               { return name; }
    public String getEmail()              { return email; }
    public Plan getCurrentPlan()          { return currentPlan; }
    public Status getStatus()             { return status; }
    public LocalDate getStartDate()       { return startDate; }
    public LocalDate getTrialEndDate()    { return trialEndDate; }
    public LocalDate getCycleStart()      { return cycleStart; }
    public LocalDate getNextBillingDate() { return nextBillingDate; }
    public double getCreditBalance()      { return creditBalance; }
    public static int getSubscriberCount(){ return subscriberCount; }

    public void setName(String name) { this.name = name; }

    public void setEmail(String email) {
        if (email == null || !email.contains("@")) {
            throw new IllegalArgumentException("Invalid email: " + email);
        }
        this.email = email;
    }

    @Override
    public String toString() {
        return String.format("%s | %-8s | %-22s | plan=%-8s | status=%-9s | next bill=%s | credit=%.2f",
                id, name, email, currentPlan == null ? "-" : currentPlan.getName(),
                status, nextBillingDate == null ? "-" : nextBillingDate, creditBalance);
    }
}
