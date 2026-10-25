import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Base class for all subscription plans.
 * Abstract: concrete pricing rules live in MonthlyPlan / AnnualPlan (inheritance + overriding).
 */
public abstract class Plan {

    private static int planCount = 0;                       // static variable

    private final String planId;
    private String name;
    private double monthlyPrice;
    private final List<String> features = new ArrayList<String>();
    private int trialDays;

    protected Plan(String planId, String name, double monthlyPrice, List<String> features, int trialDays) {
        if (planId == null || planId.trim().isEmpty()) {
            throw new IllegalArgumentException("Plan id is required");
        }
        if (monthlyPrice < 0 || trialDays < 0) {
            throw new IllegalArgumentException("Price and trial days cannot be negative");
        }
        this.planId = planId;
        this.name = name;
        this.monthlyPrice = monthlyPrice;
        if (features != null) {
            this.features.addAll(features);
        }
        this.trialDays = trialDays;
        planCount++;
    }

    // ---- polymorphic behaviour: each plan type decides how a billing cycle is charged ----
    /** Amount charged for ONE full billing cycle of this plan. */
    public abstract double computeAmount();

    /** Length of one billing cycle in months (1 = monthly, 12 = annual). */
    public abstract int getBillingCycleMonths();

    public abstract String getPlanType();

    /** Monthly-equivalent price, used for proration across different cycle lengths. */
    public double getEffectiveMonthlyPrice() {
        return computeAmount() / getBillingCycleMonths();
    }

    public static double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    public void addFeature(String feature) {
        if (feature != null && !feature.trim().isEmpty()) {
            features.add(feature);
        }
    }

    // ---- getters / setters ----
    public String getPlanId()          { return planId; }
    public String getName()            { return name; }
    public double getMonthlyPrice()    { return monthlyPrice; }
    public List<String> getFeatures()  { return Collections.unmodifiableList(features); }
    public int getTrialDays()          { return trialDays; }
    public static int getPlanCount()   { return planCount; }

    public void setName(String name) { this.name = name; }

    public void setMonthlyPrice(double monthlyPrice) {
        if (monthlyPrice < 0) throw new IllegalArgumentException("Price cannot be negative");
        this.monthlyPrice = monthlyPrice;
    }

    public void setTrialDays(int trialDays) {
        if (trialDays < 0) throw new IllegalArgumentException("Trial days cannot be negative");
        this.trialDays = trialDays;
    }

    @Override
    public String toString() {
        return String.format("%s [%s | %s] list %.2f/month, per-cycle %.2f, trial %d days, features=%s",
                planId, name, getPlanType(), monthlyPrice, computeAmount(), trialDays, features);
    }
}
