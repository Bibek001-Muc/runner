package Munich_Model_runner.scoring;

import com.google.inject.Inject;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.Plan;
import org.matsim.core.scoring.ScoringFunction;
import org.matsim.core.scoring.ScoringFunctionFactory;
import org.matsim.core.scoring.SumScoringFunction;
import org.matsim.core.scoring.functions.CharyparNagelActivityScoring;
import org.matsim.core.scoring.functions.CharyparNagelAgentStuckScoring;
import org.matsim.core.scoring.functions.ScoringParameters;
import org.matsim.core.scoring.functions.ScoringParametersForPerson;

/**
 * Wires all custom scoring components together per agent per iteration.
 * Registered in run_Simulation_custom via bindScoringFunctionFactory().
 *
 * Each agent scoring function:
 *   1. CharyparNagelActivityScoring   — standard performing/late-arrival utility
 *   2. CustomLegScoring               — VTTS travel disutility + car cost + transfers
 *   3. WeeklyFareScoring              — MVV zonal fare once per weekly plan
 *   4. CharyparNagelAgentStuckScoring — standard stuck penalty
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
        sf.addScoringFunction(new CharyparNagelActivityScoring(params));   // 1
        if (plan != null) {
            sf.addScoringFunction(new CustomLegScoring(plan));                 // 2
            sf.addScoringFunction(new WeeklyFareScoring(plan, fareCalculator));// 3
        }
        sf.addScoringFunction(new CharyparNagelAgentStuckScoring(params)); // 4
        return sf;
    }
}
