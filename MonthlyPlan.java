import java.util.List;

/** Billed every month at the list price. */
public class MonthlyPlan extends Plan {

    public MonthlyPlan(String planId, String name, double monthlyPrice, List<String> features, int trialDays) {
        super(planId, name, monthlyPrice, features, trialDays);
    }

    @Override
    public double computeAmount() {
        return round2(getMonthlyPrice());
    }

    @Override
    public int getBillingCycleMonths() { return 1; }

    @Override
    public String getPlanType() { return "Monthly"; }
}
