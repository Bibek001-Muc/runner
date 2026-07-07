package Munich_Model_runner.scoring;

import org.matsim.api.core.v01.TransportMode;
import org.matsim.api.core.v01.population.Leg;
import org.matsim.api.core.v01.population.Plan;
import org.matsim.api.core.v01.population.PlanElement;
import org.matsim.core.scoring.SumScoringFunction;

/**
 * Deducts the weekly share of the monthly 49-euro D-Ticket ONCE per agent
 * plan at finish(), IF the agent uses PT at least once in the weekly plan.
 * Flat nationwide fare — NO zonal component (replaces WeeklyFareScoring /
 * ZonalFareCalculator in the D-Ticket scenario).
 *
 * Weekly fare = 49 / 4 = 12.25 euros (7-day simulated week).
 * β_money = 1 → score contribution = -12.25 utils.
 */
public class DTicketFareScoring implements SumScoringFunction.BasicScoring {
    public static final double WEEKLY_DTICKET_FARE = 49.0 / 4.0;

    private final Plan plan;
    private double score = 0.0;

    public DTicketFareScoring(Plan plan) {
        this.plan = plan;
    }

    @Override
    public void finish() {
        score = usesPt(plan) ? -WEEKLY_DTICKET_FARE : 0.0;
    }

    private static boolean usesPt(Plan plan) {
        for (PlanElement pe : plan.getPlanElements()) {
            if (pe instanceof Leg && TransportMode.pt.equals(((Leg) pe).getMode())) {
                return true;
            }
        }
        return false;
    }

    @Override
    public double getScore() {
        return score;
    }
}
