package Munich_Model_runner.replanning;

import com.google.inject.Inject;
import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.Plan;
import org.matsim.core.config.Config;
import org.matsim.core.population.algorithms.PermissibleModesCalculator;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * Returns the set of modes a given agent is allowed to choose from
 * inside SubtourModeChoice.
 *
 * The base mode set is read from config.subtourModeChoice().getModes()
 * — NOT hardcoded. SubtourModeChoice draws its TARGET modes from this
 * calculator (not from the config directly), so hardcoding a list here
 * would silently re-enable modes a runner removed from the config.
 * That is exactly how bike leaked back into the D-Ticket scenario:
 * bike was removed from smc.setModes() but still offered here, letting
 * agents switch ONTO bike while bike subtours could never switch away.
 *
 *   driving_license == true   →  configured modes
 *   driving_license == false  →  configured modes minus "car"
 *   attribute missing/null    →  configured modes minus "car" (conservative)
 *
 * Bound in the runners via:
 *     bind(PermissibleModesCalculator.class)
 *         .to(LicensePermissibleModesCalculator.class);
 *
 * NOTE: this only constrains mode CHOICE (SubtourModeChoice strategy).
 * It does not retroactively remove existing car legs from plans that
 * the demand XML already created — those would need a pre-processing
 * step on the population.
 */
public class LicensePermissibleModesCalculator implements PermissibleModesCalculator {

    private static final String ATTR_DRIVING_LICENSE = "driving_license";

    private final List<String> allModes;
    private final List<String> noCar;

    @Inject
    public LicensePermissibleModesCalculator(Config config) {
        this.allModes = Collections.unmodifiableList(
                Arrays.asList(config.subtourModeChoice().getModes()));
        List<String> withoutCar = new ArrayList<>(this.allModes);
        withoutCar.remove("car");
        this.noCar = Collections.unmodifiableList(withoutCar);
    }

    @Override
    public Collection<String> getPermissibleModes(Plan plan) {
        Person person = plan.getPerson();
        if (person == null) return noCar; // conservative fallback
        Object attr = person.getAttributes().getAttribute(ATTR_DRIVING_LICENSE);
        return hasLicense(attr) ? allModes : noCar;
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
