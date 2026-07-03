package Munich_Model_runner.scoring;

/**
 * Provides Value of Travel Time Savings (VTTS) in euros/hour.
 * Source: postintervention period (September-October), Table 6.
 * β_money = 1 → VTTS = direct disutility per hour, no conversion needed.
 *
 * Activity type mapping:
 *   work, education          → commute
 *   shopping                 → shopping
 *   home, recreation,
 *   accompany, other,
 *   subtour                  → other
 */
public class VTTSProvider {
    // PT in-vehicle VTTS (euros/hr)
    private static final double PT_COMMUTE   = 11.18;
    private static final double PT_SHOPPING  =  9.94;
    private static final double PT_OTHER     =  9.69;
    public  static final double PT_WAIT_TIME =  7.16; // uniform across activity types
    // Car VTTS (euros/hr)
    private static final double CAR_COMMUTE  = 13.42;
    private static final double CAR_SHOPPING =  7.71;
    private static final double CAR_OTHER    =  9.33;
    // Walk VTTS (euros/hr)
    private static final double WALK_COMMUTE  = 16.86;
    private static final double WALK_SHOPPING = 13.05;
    private static final double WALK_OTHER    = 12.33;
    // Bike VTTS (euros/hr)
    private static final double BIKE_COMMUTE  = 11.00;
    private static final double BIKE_SHOPPING = 10.69;
    private static final double BIKE_OTHER    = 10.39;

    /**
     * Returns VTTS in euros/hr for the given mode and next activity type.
     * Called per leg during scoring.
     */
    public static double getVTTS(String mode, String nextActivityType) {
        if (mode == null) return PT_OTHER;
        String category = mapToCategory(nextActivityType);
        switch (mode.toLowerCase()) {
            case "pt":
            case "bus":
            case "subway":
            case "tram":
                return getPTVTTS(category);
            case "car":
            case "car_passenger": // same VTTS as car; operating cost applies to car only
                return getCarVTTS(category);
            case "walk":
                return getWalkVTTS(category);
            case "bike":
            case "bicycle":
                return getBikeVTTS(category);
            default:
                return PT_OTHER; // safe fallback
        }
    }

    private static String mapToCategory(String activityType) {
        if (activityType == null) return "other";
        switch (activityType.toLowerCase()) {
            case "work":
            case "education":
                return "commute";
            case "shopping":
                return "shopping";
            default:
                return "other";
        }
    }

    private static double getPTVTTS(String cat) {
        switch (cat) {
            case "commute":  return PT_COMMUTE;
            case "shopping": return PT_SHOPPING;
            default:         return PT_OTHER;
        }
    }

    private static double getCarVTTS(String cat) {
        switch (cat) {
            case "commute":  return CAR_COMMUTE;
            case "shopping": return CAR_SHOPPING;
            default:         return CAR_OTHER;
        }
    }

    private static double getWalkVTTS(String cat) {
        switch (cat) {
            case "commute":  return WALK_COMMUTE;
            case "shopping": return WALK_SHOPPING;
            default:         return WALK_OTHER;
        }
    }

    private static double getBikeVTTS(String cat) {
        switch (cat) {
            case "commute":  return BIKE_COMMUTE;
            case "shopping": return BIKE_SHOPPING;
            default:         return BIKE_OTHER;
        }
    }
}
