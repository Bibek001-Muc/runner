package Munich_Model_runner.scoring;

import org.matsim.api.core.v01.population.Plan;
import org.matsim.core.scoring.SumScoringFunction;

/**
 * Deducts the weekly MVV PT fare ONCE per agent plan at finish().
 * β_money = 1 → score contribution = -fare_in_euros directly in utils.
 */
public class WeeklyFareScoring implements SumScoringFunction.BasicScoring {
    private final Plan                plan;
    private final ZonalFareCalculator fareCalculator;
    private double score = 0.0;

    public WeeklyFareScoring(Plan plan, ZonalFareCalculator fareCalculator) {
        this.plan           = plan;
        this.fareCalculator = fareCalculator;
    }

    @Override
    public void finish() {
        double weeklyFare = fareCalculator.computeFare(plan);
        score = -weeklyFare; // β_money = 1, direct utils deduction
    }

    @Override
    public double getScore() {
        return score;
    }
}
