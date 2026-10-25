import java.util.List;

/** Billed once a year: 12 months of price minus an annual discount. */
public class AnnualPlan extends Plan {

    private double discountPercent;

    public AnnualPlan(String planId, String name, double monthlyPrice, List<String> features,
                      int trialDays, double discountPercent) {
        super(planId, name, monthlyPrice, features, trialDays);
        setDiscountPercent(discountPercent);
    }

    @Override
    public double computeAmount() {
        return round2(getMonthlyPrice() * 12 * (1 - discountPercent / 100.0));
    }

    @Override
    public int getBillingCycleMonths() { return 12; }

    @Override
    public String getPlanType() { return "Annual (" + discountPercent + "% off)"; }

    public double getDiscountPercent() { return discountPercent; }

    public void setDiscountPercent(double discountPercent) {
        if (discountPercent < 0 || discountPercent > 100) {
            throw new IllegalArgumentException("Discount must be between 0 and 100");
        }
        this.discountPercent = discountPercent;
    }
}
