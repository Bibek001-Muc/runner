# Munich_Model_runner

MATSim simulation project for the Munich scenario with two runners:

- **`run_Simulation_baseline`** — Single-day (24h) simulation using standard MATSim scoring.
- **`run_Simulation_custom`** — Full 7-day weekly simulation with custom activity-type-specific VTTS, weekly MVV zonal fare, and per-transfer / per-km cost terms.

## Versions

- **MATSim:** `14.0` (taken from the reference `deutschlandtkt/pom.xml`)
- **Java:** `11`
- **Build:** Maven

## Project Layout

```
Munich_Model_runner/
├── pom.xml
├── README.md
├── .gitignore
├── src/main/java/Munich_Model_runner/
│   ├── Simulation_config_run/
│   │   ├── run_Simulation_baseline.java
│   │   └── run_Simulation_custom.java
│   ├── scoring/
│   │   ├── VTTSProvider.java
│   │   ├── CustomLegScoring.java
│   │   ├── ZonalFareCalculator.java
│   │   ├── WeeklyFareScoring.java
│   │   └── CustomScoringFunctionFactory.java
│   └── replanning/
│       └── LicensePermissibleModesCalculator.java
└── scenarios/Input_and_outputFile/
    ├── Other_input_and_output_file/   (place demand / network / schedule / vehicles XML here)
    ├── Baseline_output/                (written to by baseline runner)
    └── Custom_output/                  (written to by custom runner)
```

## Required Input Files

Place the following inside `scenarios/Input_and_outputFile/Other_input_and_output_file/` before running:

- `DemandTest06.xml` — pre-generated weekly population plans (must include `driving_license` person attribute)
- `mapped_network_baseline.xml` — MATSim network
- `munichschedulemapped.xml` — transit schedule
- `MunichVehicles.xml` — transit vehicles
- `stops.txt` — GTFS stops file with `zone_id` values, used by the custom fare calculator

Activity types expected in the demand: `home`, `work`, `education`, `shopping`, `recreation`, `accompany`, `other`, `subtour`.
Leg modes expected: `pt`, `car`, `walk`, `bike`, `car_passenger`.

## Custom Scoring Notes (custom runner)

- VTTS values come from `VTTSProvider` (Table 6, postintervention). Mapping: `work`/`education` → commute, `shopping` → shopping, everything else → other.
- Car operating cost: 0.65 €/km (set both as `MonetaryDistanceCostRate` on the car mode and applied in `CustomLegScoring` — see code comments before adjusting).
- Weekly MVV pass: charged once per plan by `WeeklyFareScoring` based on the set of zones visited across the agent's full week. `ZonalFareCalculator` reads zones from GTFS `stops.txt`; mapped schedule stop ids are resolved by stripping the `.link:...` suffix. Boundary zones such as `M/1` are treated as alternatives and the cheapest valid weekly zone set is charged. You can set the file explicitly with `-Dmunich.gtfs.stops=<path>` or the `MUNICH_GTFS_STOPS` environment variable.
- β_money = 1, so fares and per-km costs map directly to utils.
- PT wait time: 7.16 €/hr (global, via config).
- β_transfer = 1 €/transfer (applied per PT journey in `CustomLegScoring`).
- The `work` activity in the custom runner has **no** opening/closing/latest-start window because plans span 7 days.

## Mode Choice & Driving License (custom runner)

The custom runner adds `SubtourModeChoice` so agents can shift between modes across iterations:

- `modes = {car, pt, bike, walk}`
- `chainBasedModes = {car, bike}` (subtour must return to origin)
- Strategy mix: `ReRoute 0.6 / SubtourModeChoice 0.2 / SelectRandom 0.2`
- Innovation disabled in the last 20% of iterations so agents converge on their best plan

`LicensePermissibleModesCalculator` is bound as the `PermissibleModesCalculator`. For each plan it reads the person attribute `driving_license`:

- `true`  → `{car, pt, bike, walk}`
- `false` / missing → `{pt, bike, walk}` (car removed)

This only restricts the mode-CHOICE strategy. If the demand XML already assigns a car leg to a non-license holder, it stays until that subtour is re-chosen — there's no retroactive pruning of existing plans. Pre-process the population if you need to scrub those out first.

**Iteration count:** kept at 30 in this configuration. Note that mode choice typically benefits from 100–300 iterations for proper convergence; bump `setLastIteration` if results look unstable across runs.

## Running

From the project root:

```bash
mvn -q -DskipTests package
mvn -q exec:java -Dexec.mainClass=Munich_Model_runner.Simulation_config_run.run_Simulation_baseline
mvn -q exec:java -Dexec.mainClass=Munich_Model_runner.Simulation_config_run.run_Simulation_custom
```

Or run either `main()` directly from IntelliJ IDEA.
