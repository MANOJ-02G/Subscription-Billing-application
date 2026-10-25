import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * An invoice. Its state is private and only changes through applyPayment(),
 * markOverdue() and cancel() (encapsulation).
 */
public class Invoice {

    public enum State { ISSUED, PARTIALLY_PAID, PAID, OVERDUE, CANCELLED }

    private static final double EPS = 0.005;
    private static int counter = 0;

    private final String invoiceNo;
    private final String subscriberId;
    private final String planId;
    private final String description;
    private final double amount;
    private final LocalDate issueDate;
    private final LocalDate dueDate;
    private double amountPaid;
    private State state;

    public Invoice(String subscriberId, String planId, double amount,
                   LocalDate issueDate, LocalDate dueDate, String description) {
        counter++;
        this.invoiceNo = String.format("INV-%04d", counter);
        this.subscriberId = subscriberId;
        this.planId = planId;
        this.amount = Plan.round2(amount);
        this.issueDate = issueDate;
        this.dueDate = dueDate;
        this.description = description;
        this.amountPaid = 0.0;
        this.state = (this.amount <= EPS) ? State.PAID : State.ISSUED;   // fully covered by credit
    }

    public double getBalance() {
        return Plan.round2(amount - amountPaid);
    }

    public boolean isOpen() {
        return state == State.ISSUED || state == State.PARTIALLY_PAID || state == State.OVERDUE;
    }

    public boolean isPastDue(LocalDate asOf) {
        return isOpen() && asOf.isAfter(dueDate);
    }

    public long daysOverdue(LocalDate asOf) {
        return isPastDue(asOf) ? ChronoUnit.DAYS.between(dueDate, asOf) : 0;
    }

    public void applyPayment(double payment) {
        if (!isOpen()) throw new IllegalStateException(invoiceNo + " is " + state + " and cannot accept payments");
        if (payment <= 0) throw new IllegalArgumentException("Payment must be positive");
        if (payment > getBalance() + EPS) {
            throw new IllegalArgumentException(String.format("Payment %.2f exceeds balance %.2f", payment, getBalance()));
        }
        amountPaid = Plan.round2(amountPaid + payment);
        if (getBalance() <= EPS) {
            state = State.PAID;
        } else if (state != State.OVERDUE) {
            state = State.PARTIALLY_PAID;
        }
    }

    public boolean markOverdue(LocalDate asOf) {
        if (isPastDue(asOf) && state != State.OVERDUE) {
            state = State.OVERDUE;
            return true;
        }
        return false;
    }

    public void cancel() {
        if (amountPaid > EPS) throw new IllegalStateException("Cannot cancel an invoice with payments");
        if (state == State.PAID) throw new IllegalStateException("Cannot cancel a paid invoice");
        state = State.CANCELLED;
    }

    // ---- getters ----
    public String getInvoiceNo()     { return invoiceNo; }
    public String getSubscriberId()  { return subscriberId; }
    public String getPlanId()        { return planId; }
    public String getDescription()   { return description; }
    public double getAmount()        { return amount; }
    public double getAmountPaid()    { return amountPaid; }
    public LocalDate getIssueDate()  { return issueDate; }
    public LocalDate getDueDate()    { return dueDate; }
    public State getState()          { return state; }
    public static int getInvoiceCount() { return counter; }

    @Override
    public String toString() {
        return String.format("%s | %s | amt %9.2f | paid %9.2f | due %s | %-14s | %s",
                invoiceNo, subscriberId, amount, amountPaid, dueDate, state, description);
    }
}
