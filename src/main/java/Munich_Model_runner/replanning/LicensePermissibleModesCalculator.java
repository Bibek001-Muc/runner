package Munich_Model_runner.replanning;

import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.Plan;
import org.matsim.core.population.algorithms.PermissibleModesCalculator;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * Returns the set of modes a given agent is allowed to choose from
 * inside SubtourModeChoice.
 *
 *   driving_license == true   →  {car, pt, bike, walk}
 *   driving_license == false  →  {pt, bike, walk}     (car removed)
 *   attribute missing/null    →  {pt, bike, walk}     (conservative)
 *
 * Bound in run_Simulation_custom via:
 *     bind(PermissibleModesCalculator.class)
 *         .to(LicensePermissibleModesCalculator.class);
 *
 * NOTE: this only constrains mode CHOICE (SubtourModeChoice strategy).
 * It does not retroactively remove existing car legs from plans that
 * the demand XML already created — those would need a pre-processing
 * step on the population.
 */
public class LicensePermissibleModesCalculator implements PermissibleModesCalculator {

    private static final List<String> ALL_MODES = Collections.unmodifiableList(
            Arrays.asList("car", "pt", "bike", "walk"));

    private static final List<String> NO_CAR = Collections.unmodifiableList(
            Arrays.asList("pt", "bike", "walk"));

    private static final String ATTR_DRIVING_LICENSE = "driving_license";

    @Override
    public Collection<String> getPermissibleModes(Plan plan) {
        Person person = plan.getPerson();
        if (person == null) return NO_CAR; // conservative fallback
        Object attr = person.getAttributes().getAttribute(ATTR_DRIVING_LICENSE);
        return hasLicense(attr) ? ALL_MODES : NO_CAR;
    }

    /**
     * Robust truthy check — handles Boolean, String ("true"/"yes"/"1"),
     * and numeric values written by various tooling.
     */
    private static boolean hasLicense(Object attr) {
        if (attr == null) return false;
        if (attr instanceof Boolean) return (Boolean) attr;
        if (attr instanceof Number)  return ((Number) attr).intValue() != 0;
        String s = attr.toString().trim().toLowerCase();
        return s.equals("true") || s.equals("yes") || s.equals("1");
    }
}
