package Munich_Model_runner.scoring;

import com.google.inject.Inject;
import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.Plan;
import org.matsim.core.scoring.ScoringFunction;
import org.matsim.core.scoring.ScoringFunctionFactory;
import org.matsim.core.scoring.SumScoringFunction;
import org.matsim.core.scoring.functions.CharyparNagelAgentStuckScoring;
import org.matsim.core.scoring.functions.ScoringParameters;
import org.matsim.core.scoring.functions.ScoringParametersForPerson;

/**
 * D-Ticket variant of CustomScoringFunctionFactory.
 * Registered in dticket_Simulation_custom via bindScoringFunctionFactory().
 *
 * Each agent scoring function:
 *   1. CustomLegScoring               — VTTS travel disutility + car cost
 *                                       + PT wait + transfers
 *   2. DTicketFareScoring             — flat 49/4 euros once per weekly plan
 *                                       if the agent uses PT at all
 *   3. CharyparNagelAgentStuckScoring — stuck penalty (uses lateArrival)
 *
 * NO activity scoring — plans are compared on travel disutility alone
 * (same rationale as CustomScoringFunctionFactory).
 *
 * Unlike the zonal variant, no ZonalFareCalculator / GTFS stops.txt is
 * needed — the D-Ticket fare is flat and zone-independent.
 */
public class DTicketScoringFunctionFactory implements ScoringFunctionFactory {
    private final ScoringParametersForPerson parametersForPerson;

    @Inject
    public DTicketScoringFunctionFactory(ScoringParametersForPerson parametersForPerson) {
        this.parametersForPerson = parametersForPerson;
    }

    @Override
    public ScoringFunction createNewScoringFunction(Person person) {
        SumScoringFunction sf    = new SumScoringFunction();
        Plan plan                = person.getSelectedPlan();
        ScoringParameters params = parametersForPerson.getScoringParameters(person);
        if (plan != null) {
            sf.addScoringFunction(new CustomLegScoring(plan));      // 1
            sf.addScoringFunction(new DTicketFareScoring(plan));    // 2
        }
        sf.addScoringFunction(new CharyparNagelAgentStuckScoring(params)); // 3
        return sf;
    }
}
