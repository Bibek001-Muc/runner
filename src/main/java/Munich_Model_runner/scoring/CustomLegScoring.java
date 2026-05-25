package Munich_Model_runner.scoring;

import org.matsim.api.core.v01.population.Activity;
import org.matsim.api.core.v01.population.Leg;
import org.matsim.api.core.v01.population.Plan;
import org.matsim.api.core.v01.population.PlanElement;
import org.matsim.core.scoring.SumScoringFunction;

import java.util.ArrayList;
import java.util.List;

/**
 * Custom leg scoring applying activity-type-specific VTTS values.
 *
 * Per leg score:
 *   ALL modes : -VTTS(mode, nextActivity) * travelTime_hr
 *   Car only  : -0.65 euros/km  (total vehicle operating cost)
 *   PT only   : -β_transfer * numTransfers (β_transfer = 1 euro/transfer)
 *
 * PT wait time scored globally via config (7.16 euros/hr).
 * Plan is preprocessed once in constructor for efficiency.
 */
public class CustomLegScoring implements SumScoringFunction.LegScoring {
    private static final double CAR_COST_PER_KM = 0.65; // euros/km
    private static final double BETA_TRANSFER   = 1.0;  // euros/transfer
    private final List<String>  nextActivityPerLeg = new ArrayList<>();
    private final List<Integer> transfersPerLeg    = new ArrayList<>();
    private int    legIndex = 0;
    private double score    = 0.0;

    public CustomLegScoring(Plan plan) {
        preprocessPlan(plan);
    }

    private void preprocessPlan(Plan plan) {
        List<PlanElement> elements = plan.getPlanElements();
        for (int i = 0; i < elements.size(); i++) {
            if (!(elements.get(i) instanceof Leg)) continue;
            Leg currentLeg = (Leg) elements.get(i);
            // Find next real activity — skip pt interaction placeholders
            String nextActType = "other";
            for (int j = i + 1; j < elements.size(); j++) {
                if (elements.get(j) instanceof Activity) {
                    Activity act = (Activity) elements.get(j);
                    if (!"pt interaction".equalsIgnoreCase(act.getType())) {
                        nextActType = act.getType();
                        break;
                    }
                }
            }
            nextActivityPerLeg.add(nextActType);
            // Transfer penalty — only for PT, only on first PT leg of journey
            if (isPTMode(currentLeg.getMode())) {
                if (isFirstPTLegInJourney(elements, i)) {
                    int ptLegs = countPTLegsInJourney(elements, i);
                    transfersPerLeg.add(Math.max(0, ptLegs - 1));
                } else {
                    transfersPerLeg.add(0);
                }
            } else {
                transfersPerLeg.add(0);
            }
        }
    }

    private boolean isFirstPTLegInJourney(List<PlanElement> elements, int currentIdx) {
        for (int j = currentIdx - 1; j >= 0; j--) {
            PlanElement pe = elements.get(j);
            if (pe instanceof Activity) {
                String type = ((Activity) pe).getType();
                if ("pt interaction".equalsIgnoreCase(type)) continue;
                else return true;
            }
            if (pe instanceof Leg) {
                Leg prev = (Leg) pe;
                if (isPTMode(prev.getMode()))              return false;
                if ("walk".equalsIgnoreCase(prev.getMode())) continue;
                return true;
            }
        }
        return true;
    }

    private int countPTLegsInJourney(List<PlanElement> elements, int startIdx) {
        int count = 0;
        for (int j = startIdx; j < elements.size(); j++) {
            PlanElement pe = elements.get(j);
            if (pe instanceof Leg && isPTMode(((Leg) pe).getMode())) {
                count++;
            } else if (pe instanceof Activity) {
                if (!"pt interaction".equalsIgnoreCase(((Activity) pe).getType())) break;
            }
        }
        return count;
    }

    private boolean isPTMode(String mode) {
        if (mode == null) return false;
        switch (mode.toLowerCase()) {
            case "pt": case "bus": case "subway": case "tram": return true;
            default: return false;
        }
    }

    @Override
    public void handleLeg(Leg leg) {
        String mode      = leg.getMode();
        String nextAct   = legIndex < nextActivityPerLeg.size()
                ? nextActivityPerLeg.get(legIndex) : "other";
        int    transfers = legIndex < transfersPerLeg.size()
                ? transfersPerLeg.get(legIndex) : 0;
        double travelTimeHr = leg.getTravelTime().isDefined()
                ? leg.getTravelTime().seconds() / 3600.0
                : 0.0;
        // Travel time disutility
        double legScore = -VTTSProvider.getVTTS(mode, nextAct) * travelTimeHr;
        // Car distance cost
        if ("car".equalsIgnoreCase(mode)
                && leg.getRoute() != null
                && leg.getRoute().getDistance() > 0) {
            legScore -= CAR_COST_PER_KM * (leg.getRoute().getDistance() / 1000.0);
        }
        // Transfer penalty
        if (isPTMode(mode) && transfers > 0) {
            legScore -= BETA_TRANSFER * transfers;
        }
        score += legScore;
        legIndex++;
    }

    @Override public void finish()     { }
    @Override public double getScore() { return score; }
}
