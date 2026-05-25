package Munich_Model_runner.scoring;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.population.Leg;
import org.matsim.api.core.v01.population.Plan;
import org.matsim.api.core.v01.population.PlanElement;
import org.matsim.pt.routes.TransitPassengerRoute;
import org.matsim.pt.transitSchedule.api.TransitSchedule;
import org.matsim.pt.transitSchedule.api.TransitStopFacility;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Computes the weekly MVV zonal fare for an agent's entire 7-day plan.
 *
 * Zone system: M, 1, 2, 3, 4, 5, 6.
 *
 * Fare formula:
 * Trips involving zone M:
 * fare = 19.93 + 8.66 * n, where n is the number of non-M zones.
 *
 * Trips not involving zone M:
 * fare = 18.60 if total zones s <= 2
 * fare = 4.52 + 8.34 * s if total zones s >= 3
 *
 * Zones are read from GTFS stops.txt, not from the mapped schedule XML.
 * pt2matsim mapped stop ids keep the original GTFS stop id before ".link:",
 * so "db_185312.link:pt_db_185312" resolves to the GTFS row "db_185312".
 * Boundary zones such as "M/1" are treated as alternatives, and the cheapest
 * valid weekly zone set is charged.
 */
public class    ZonalFareCalculator {
    private static final Logger LOG = LogManager.getLogger(ZonalFareCalculator.class);

    public static final String ZONE_ATTRIBUTE = "zone";
    public static final String GTFS_STOPS_PROPERTY = "munich.gtfs.stops";
    public static final String GTFS_STOPS_ENV = "MUNICH_GTFS_STOPS";
    public static final String ZONE_M = "M";

    public static final Set<String> VALID_ZONES = new HashSet<>();
    static {
        VALID_ZONES.add("M");
        VALID_ZONES.add("1");
        VALID_ZONES.add("2");
        VALID_ZONES.add("3");
        VALID_ZONES.add("4");
        VALID_ZONES.add("5");
        VALID_ZONES.add("6");
    }

    private final TransitSchedule transitSchedule;
    private final Map<String, String> zoneByGtfsStopId;

    public ZonalFareCalculator(TransitSchedule transitSchedule) {
        this(transitSchedule, resolveGtfsStopsPath());
    }

    public ZonalFareCalculator(TransitSchedule transitSchedule, Path gtfsStopsFile) {
        this.transitSchedule = transitSchedule;
        this.zoneByGtfsStopId = loadGtfsZones(gtfsStopsFile);
    }

    /**
     * Returns the weekly fare in euros for this agent's plan.
     * Returns 0.0 if the agent makes no PT trips.
     */
    public double computeFare(Plan plan) {
        Set<Set<String>> zoneAlternatives = collectZoneAlternativesFromPlan(plan);
        if (zoneAlternatives.isEmpty()) return 0.0;

        double bestFare = Double.POSITIVE_INFINITY;
        for (Set<String> zonesVisited : zoneAlternatives) {
            if (!zonesVisited.isEmpty()) {
                bestFare = Math.min(bestFare, applyFareFormula(zonesVisited));
            }
        }
        return Double.isFinite(bestFare) ? bestFare : 0.0;
    }

    /**
     * Collects possible unique-zone sets from all PT legs across the full weekly
     * plan. Boundary stops like "M/1" create two alternatives.
     */
    private Set<Set<String>> collectZoneAlternativesFromPlan(Plan plan) {
        Set<Set<String>> alternatives = new HashSet<>();
        alternatives.add(new HashSet<>());
        boolean foundAtLeastOneZone = false;

        for (PlanElement pe : plan.getPlanElements()) {
            if (!(pe instanceof Leg)) continue;
            Leg leg = (Leg) pe;
            if (!(leg.getRoute() instanceof TransitPassengerRoute)) continue;

            TransitPassengerRoute ptRoute = (TransitPassengerRoute) leg.getRoute();
            Set<String> accessOptions = zoneOptionsFromStop(ptRoute.getAccessStopId());
            Set<String> egressOptions = zoneOptionsFromStop(ptRoute.getEgressStopId());

            if (!accessOptions.isEmpty()) {
                alternatives = addZoneOptions(alternatives, accessOptions);
                foundAtLeastOneZone = true;
            }
            if (!egressOptions.isEmpty()) {
                alternatives = addZoneOptions(alternatives, egressOptions);
                foundAtLeastOneZone = true;
            }
        }
        return foundAtLeastOneZone ? alternatives : new HashSet<>();
    }

    private Set<String> zoneOptionsFromStop(Id<TransitStopFacility> stopId) {
        if (stopId == null) return new HashSet<>();

        TransitStopFacility facility = transitSchedule.getFacilities().get(stopId);

        if (facility != null) {
            Object zoneAttr = facility.getAttributes().getAttribute(ZONE_ATTRIBUTE);
            Set<String> zoneOptions = zoneOptionsFromRawValue(zoneAttr);
            if (!zoneOptions.isEmpty()) return zoneOptions;
        }

        String originalGtfsStopId = originalGtfsStopId(stopId.toString());
        Set<String> zoneOptions = zoneOptionsFromRawValue(zoneByGtfsStopId.get(originalGtfsStopId));
        if (!zoneOptions.isEmpty()) return zoneOptions;

        if (facility != null && facility.getStopAreaId() != null) {
            return zoneOptionsFromRawValue(zoneByGtfsStopId.get(facility.getStopAreaId().toString()));
        }
        return new HashSet<>();
    }

    private static Set<Set<String>> addZoneOptions(Set<Set<String>> alternatives, Set<String> zoneOptions) {
        Set<Set<String>> expanded = new HashSet<>();
        for (Set<String> existing : alternatives) {
            for (String zone : zoneOptions) {
                Set<String> next = new HashSet<>(existing);
                next.add(zone);
                expanded.add(next);
            }
        }
        return expanded;
    }

    /**
     * pt2matsim mapped stop ids look like "originalStopId.link:networkLinkId".
     * Fare zones are keyed by the original GTFS stop id.
     */
    static String originalGtfsStopId(String scheduleStopId) {
        int linkSuffix = scheduleStopId.indexOf(".link:");
        return linkSuffix >= 0 ? scheduleStopId.substring(0, linkSuffix) : scheduleStopId;
    }

    private static Set<String> zoneOptionsFromRawValue(Object rawZoneValue) {
        Set<String> zones = new HashSet<>();
        if (rawZoneValue == null) return zones;

        String raw = rawZoneValue.toString().trim();
        if (raw.isEmpty() || raw.equalsIgnoreCase("k.A.")) return zones;

        for (String token : raw.split("[/;,\\s]+")) {
            String zone = token.trim();
            if (VALID_ZONES.contains(zone)) {
                zones.add(zone);
            }
        }
        return zones;
    }

    private static Path resolveGtfsStopsPath() {
        String configured = System.getProperty(GTFS_STOPS_PROPERTY);
        if (configured == null || configured.trim().isEmpty()) {
            configured = System.getenv(GTFS_STOPS_ENV);
        }
        if (configured != null && !configured.trim().isEmpty()) {
            Path path = Paths.get(configured.trim()).toAbsolutePath().normalize();
            if (!Files.isRegularFile(path)) {
                throw new IllegalStateException("Configured GTFS stops.txt does not exist: " + path);
            }
            return path;
        }

        for (Path candidate : Arrays.asList(
                Paths.get("scenarios", "Input_and_outputFile", "Other_input_and_output_file", "stops.txt"),
                Paths.get("scenarios", "Input_and_outputFile", "Other_input_and_output_file", "gtfs_unpacked", "stops.txt"),
                Paths.get("..", "..", "bibek final", "output", "gtfs_unpacked", "stops.txt")
        )) {
            Path absolute = candidate.toAbsolutePath().normalize();
            if (Files.isRegularFile(absolute)) {
                return absolute;
            }
        }

        throw new IllegalStateException(
                "GTFS stops.txt not found. Put stops.txt in scenarios/Input_and_outputFile/Other_input_and_output_file, "
                        + "or set -D" + GTFS_STOPS_PROPERTY + "=<path>, or set " + GTFS_STOPS_ENV + ".");
    }

    private static Map<String, String> loadGtfsZones(Path gtfsStopsFile) {
        Map<String, String> zones = new LinkedHashMap<>();
        int rows = 0;
        int rowsWithZones = 0;

        try (Reader reader = Files.newBufferedReader(gtfsStopsFile, StandardCharsets.UTF_8);
             CSVParser parser = CSVFormat.DEFAULT
                     .withFirstRecordAsHeader()
                     .withIgnoreSurroundingSpaces()
                     .parse(reader)) {

            String stopIdHeader = findHeader(parser, "stop_id");
            String zoneIdHeader = findHeader(parser, "zone_id");

            for (CSVRecord record : parser) {
                rows++;
                String stopId = record.get(stopIdHeader).trim();
                String zoneId = record.get(zoneIdHeader).trim();
                if (!stopId.isEmpty() && !zoneId.isEmpty()) {
                    zones.put(stopId, zoneId);
                    rowsWithZones++;
                }
            }
        } catch (IOException e) {
            throw new IllegalStateException("Could not read GTFS stops zones from " + gtfsStopsFile, e);
        }

        LOG.info("Loaded {} GTFS stop zones from {} ({} rows, {} rows with zone_id)",
                zones.size(), gtfsStopsFile, rows, rowsWithZones);
        return zones;
    }

    private static String findHeader(CSVParser parser, String expected) {
        for (String header : parser.getHeaderMap().keySet()) {
            if (expected.equals(header.replace("\uFEFF", "").trim())) {
                return header;
            }
        }
        throw new IllegalStateException("GTFS stops.txt is missing required column: " + expected);
    }

    private double applyFareFormula(Set<String> zones) {
        if (zones.contains(ZONE_M)) {
            int n = zones.size() - 1;
            return 19.93 + 8.66 * n;
        } else {
            int s = zones.size();
            return (s <= 2) ? 18.60 : (4.52 + 8.34 * s);
        }
    }
}
