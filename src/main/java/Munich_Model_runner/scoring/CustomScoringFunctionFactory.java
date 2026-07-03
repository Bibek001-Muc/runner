package Munich_Model_runner.scoring;

import com.google.inject.Inject;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.Plan;
import org.matsim.core.scoring.ScoringFunction;
import org.matsim.core.scoring.ScoringFunctionFactory;
import org.matsim.core.scoring.SumScoringFunction;
import org.matsim.core.scoring.functions.CharyparNagelAgentStuckScoring;
import org.matsim.core.scoring.functions.ScoringParameters;
import org.matsim.core.scoring.functions.ScoringParametersForPerson;

/**
 * Wires all custom scoring components together per agent per iteration.
 * Registered in run_Simulation_custom via bindScoringFunctionFactory().
 *
 * Each agent scoring function:
 *   1. CustomLegScoring               — VTTS travel disutility + car cost
 *                                       + PT wait + transfers
 *   2. WeeklyFareScoring              — MVV zonal fare once per weekly plan
 *   3. CharyparNagelAgentStuckScoring — stuck penalty (uses lateArrival)
 *
 * NO activity scoring — plans are compared on travel disutility alone.
 * Deliberate: replanning never mutates activity timings, so performing
 * utility would only distort the VTTS-based mode/route trade-offs; the
 * full Table 6 VTTS is therefore charged per hour in CustomLegScoring.
 * The activity typicalDurations in the config are inert as a result.
 *
 * ZonalFareCalculator is a shared read-only instance (thread-safe).
 */
public class CustomScoringFunctionFactory implements ScoringFunctionFactory {
    private final Scenario                   scenario;
    private final ScoringParametersForPerson parametersForPerson;
    private final ZonalFareCalculator        fareCalculator;

    @Inject
    public CustomScoringFunctionFactory(Scenario scenario,
                                        ScoringParametersForPerson parametersForPerson) {
        this.scenario            = scenario;
        this.parametersForPerson = parametersForPerson;
        this.fareCalculator      = new ZonalFareCalculator(scenario.getTransitSchedule());
    }

    @Override
    public ScoringFunction createNewScoringFunction(Person person) {
        SumScoringFunction sf    = new SumScoringFunction();
        Plan plan                = person.getSelectedPlan();
        ScoringParameters params = parametersForPerson.getScoringParameters(person);
        if (plan != null) {
            sf.addScoringFunction(new CustomLegScoring(plan));                 // 1
            sf.addScoringFunction(new WeeklyFareScoring(plan, fareCalculator));// 2
        }
        sf.addScoringFunction(new CharyparNagelAgentStuckScoring(params)); // 3
        return sf;
    }
}
