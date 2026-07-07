package Munich_Model_runner.Simulation_config_run;

import Munich_Model_runner.replanning.LicensePermissibleModesCalculator;
import Munich_Model_runner.scoring.DTicketScoringFunctionFactory;
import com.google.common.collect.Sets;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.config.groups.ChangeModeConfigGroup;
import org.matsim.core.config.groups.PlanCalcScoreConfigGroup;
import org.matsim.core.config.groups.StrategyConfigGroup;
import org.matsim.core.config.groups.SubtourModeChoiceConfigGroup;
import org.matsim.core.controler.AbstractModule;
import org.matsim.core.controler.Controler;
import org.matsim.core.controler.OutputDirectoryHierarchy;
import org.matsim.core.network.NetworkUtils;
import org.matsim.core.network.algorithms.TransportModeNetworkFilter;
import org.matsim.core.population.algorithms.PermissibleModesCalculator;
import org.matsim.core.population.algorithms.XY2Links;
import org.matsim.core.replanning.modules.SubtourModeChoice;
import org.matsim.core.replanning.strategies.DefaultPlanStrategiesModule;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.core.utils.geometry.transformations.TransformationFactory;

import java.util.HashSet;
import java.util.Set;

/**
 * Custom simulation runner — 7-day weekly plans with:
 *   - Activity-type-specific VTTS (postintervention, Table 6)
 *   - Monthly D-Ticket: flat 49/4 = 12.25 euros deducted once per weekly
 *     plan IF the agent uses PT at all (no zonal fare in this scenario)
 *   - β_money = 1, β_transfer = 1 euro/transfer
 *   - PT wait VTTS = 7.16 euros/hr
 *   - Car operating cost = 0.65 euros/km (all-in)
 *
 * Work activity: NO opening/closing/latestStart time constraints.
 * Reason: demand is pre-generated weekly — MATSim must not penalise
 * agents whose work activities fall outside a single-day time window.
 */
public class dticket_Simulation_custom {
    public static void main(String[] args) {
        Config emptyConfig = ConfigUtils.createConfig();
        emptyConfig.global().setRandomSeed(4711);
        emptyConfig.global().setCoordinateSystem(TransformationFactory.GK4);
        // bike is a plain teleported mode (see teleportedModeSpeed below);
        // the bicycle contrib is intentionally NOT used.
        emptyConfig.plans().setInputFile("input/DemandTest06.xml");
        emptyConfig.network().setInputFile("input/munichMultimodalMapped.xml.gz");
        emptyConfig.transit().setUseTransit(true);
        emptyConfig.transit().setTransitScheduleFile("input/MunichScheduleMapped.xml");
        emptyConfig.transit().setVehiclesFile("input/MunichVehicles.xml");
        emptyConfig.transit().setTransitModes(Sets.newHashSet(TransportMode.pt));
        emptyConfig.qsim().setFlowCapFactor(0.025);
        emptyConfig.qsim().setStorageCapFactor(0.045);
        emptyConfig.qsim().setStartTime(0);
        emptyConfig.qsim().setEndTime(7 * 24 * 3600); // 7-day weekly simulation
        emptyConfig.qsim().setSnapshotPeriod(0);
        emptyConfig.qsim().setMainModes(Sets.newHashSet("car"));
        // Default TravelTimeCalculator maxTime is 30h — link travel times
        // observed after hour 30 would be discarded, leaving days 2-7
        // without congestion feedback. Extend to the full week.
        emptyConfig.travelTimeCalculator().setMaxTime(7 * 24 * 3600);
        // ── Scoring globals ────────────────────────────────────────────────
        emptyConfig.planCalcScore().setLearningRate(1);
        emptyConfig.planCalcScore().setBrainExpBeta(2);
        emptyConfig.planCalcScore().setLateArrival_utils_hr(-18);
        emptyConfig.planCalcScore().setEarlyDeparture_utils_hr(0);
        emptyConfig.planCalcScore().setPerforming_utils_hr(6);
        emptyConfig.planCalcScore().setMarginalUtilityOfMoney(1.0); // β_money = 1
        // PT wait VTTS (7.16 euros/hr) is scored in CustomLegScoring from
        // events (departure -> vehicle entry), NOT via this config group —
        // the default leg scoring that would read it is replaced anyway.
        emptyConfig.planCalcScore().setMarginalUtlOfWaiting_utils_hr(0);
        // car_passenger is a NETWORK routing mode: legs are routed on the car
        // network using the congested car travel times of the previous
        // iteration (see travel time binding below). Because it is NOT a
        // qsim main mode, the mobsim teleports the agent along that route
        // using the congested travel time — it adds no vehicle to the flow.
        emptyConfig.plansCalcRoute().setNetworkModes(Sets.newHashSet("car", "car_passenger"));
        emptyConfig.plansCalcRoute().setTeleportedModeSpeed("walk", 5.0 / 3.6);
        emptyConfig.plansCalcRoute().setTeleportedModeSpeed("bike", 15.0 / 3.6);
        // ── Mode parameters ────────────────────────────────────────────────
        // Base values below are defaults only.
        // CustomLegScoring overrides them dynamically per leg using VTTS.
        PlanCalcScoreConfigGroup.ModeParams ptParams = new PlanCalcScoreConfigGroup.ModeParams("pt");
        ptParams.setMarginalUtilityOfTraveling(-9.69); // PT_other as base
        emptyConfig.planCalcScore().addModeParams(ptParams);
        PlanCalcScoreConfigGroup.ModeParams carParams = new PlanCalcScoreConfigGroup.ModeParams("car");
        carParams.setMarginalUtilityOfTraveling(-9.33);    // Car_other as base
        carParams.setMonetaryDistanceRate(-0.65);          // 0.65 euros/km all-in
        emptyConfig.planCalcScore().addModeParams(carParams);
        PlanCalcScoreConfigGroup.ModeParams walkParams = new PlanCalcScoreConfigGroup.ModeParams("walk");
        walkParams.setMarginalUtilityOfTraveling(-12.33);  // Walk_other as base
        emptyConfig.planCalcScore().addModeParams(walkParams);
        PlanCalcScoreConfigGroup.ModeParams bikeParams = new PlanCalcScoreConfigGroup.ModeParams("bike");
        bikeParams.setMarginalUtilityOfTraveling(-10.39);  // Bike_other as base
        emptyConfig.planCalcScore().addModeParams(bikeParams);
        PlanCalcScoreConfigGroup.ModeParams busParams = new PlanCalcScoreConfigGroup.ModeParams("bus");
        emptyConfig.planCalcScore().addModeParams(busParams);
        PlanCalcScoreConfigGroup.ModeParams subwayParams = new PlanCalcScoreConfigGroup.ModeParams("subway");
        emptyConfig.planCalcScore().addModeParams(subwayParams);
        PlanCalcScoreConfigGroup.ModeParams tramParams = new PlanCalcScoreConfigGroup.ModeParams("tram");
        emptyConfig.planCalcScore().addModeParams(tramParams);
        PlanCalcScoreConfigGroup.ModeParams carPassengerParams = new PlanCalcScoreConfigGroup.ModeParams("car_passenger");
        emptyConfig.planCalcScore().addModeParams(carPassengerParams);
        // ── Activity parameters ────────────────────────────────────────────
        // IMPORTANT: work has NO opening/closing/latestStart constraints.
        // Demand is pre-generated for 7 days — MATSim must not penalise
        // agents whose work activities span beyond a single-day window.
        // typicalDuration is scaled to weekly total (5 working days * 8hrs).
        PlanCalcScoreConfigGroup.ActivityParams home = new PlanCalcScoreConfigGroup.ActivityParams("home");
        home.setPriority(1);
        home.setTypicalDuration(7 * 12 * 60 * 60); // 7 days * 12hr typical
        emptyConfig.planCalcScore().addActivityParams(home);
        PlanCalcScoreConfigGroup.ActivityParams work = new PlanCalcScoreConfigGroup.ActivityParams("work");
        work.setPriority(1);
        work.setTypicalDuration(5 * 8 * 60 * 60);  // 5 working days * 8hr
        // No opening/closing/latestStart — weekly pre-generated demand
        emptyConfig.planCalcScore().addActivityParams(work);
        PlanCalcScoreConfigGroup.ActivityParams accompany = new PlanCalcScoreConfigGroup.ActivityParams("accompany");
        accompany.setTypicalDuration(7 * 3600);     // weekly total
        emptyConfig.planCalcScore().addActivityParams(accompany);
        PlanCalcScoreConfigGroup.ActivityParams education = new PlanCalcScoreConfigGroup.ActivityParams("education");
        education.setTypicalDuration(5 * 14400);    // 5 days * 4hr
        emptyConfig.planCalcScore().addActivityParams(education);
        PlanCalcScoreConfigGroup.ActivityParams other = new PlanCalcScoreConfigGroup.ActivityParams("other");
        other.setTypicalDuration(7 * 7200);         // weekly total
        emptyConfig.planCalcScore().addActivityParams(other);
        PlanCalcScoreConfigGroup.ActivityParams recreation = new PlanCalcScoreConfigGroup.ActivityParams("recreation");
        recreation.setTypicalDuration(7 * 7200);    // weekly total
        emptyConfig.planCalcScore().addActivityParams(recreation);
        PlanCalcScoreConfigGroup.ActivityParams shopping = new PlanCalcScoreConfigGroup.ActivityParams("shopping");
        shopping.setTypicalDuration(7 * 3600);      // weekly total
        emptyConfig.planCalcScore().addActivityParams(shopping);
        PlanCalcScoreConfigGroup.ActivityParams subtour = new PlanCalcScoreConfigGroup.ActivityParams("subtour");
        subtour.setTypicalDuration(7 * 3600);       // weekly total
        emptyConfig.planCalcScore().addActivityParams(subtour);
        // Suppresses scoring of PT boarding/alighting interaction stops
        PlanCalcScoreConfigGroup.ActivityParams ptInteraction = new PlanCalcScoreConfigGroup.ActivityParams("pt interaction");
        ptInteraction.setTypicalDuration(0);
        ptInteraction.setScoringThisActivityAtAll(false);
        emptyConfig.planCalcScore().addActivityParams(ptInteraction);
        // ── SubtourModeChoice config ───────────────────────────────────────
        // Agents can switch among {car, pt, walk} on a per-subtour basis.
        // chainBasedModes = {car} → the mode of a subtour must return
        //                   to its origin (you can't leave the car at work).
        // considerCarAvailability = true → MATSim consults the bound
        //                   PermissibleModesCalculator (our custom one below)
        //                   to filter modes per agent. Agents without
        //                   driving_license cannot pick "car".
        SubtourModeChoiceConfigGroup smc = emptyConfig.subtourModeChoice();
        smc.setModes(new String[] {"car", "pt", "walk"});
        smc.setChainBasedModes(new String[] {"car"});
        smc.setConsiderCarAvailability(true);
        // Fixed car_passenger AND bike mode shares:
        // fromSpecifiedModesToSpecifiedModes means only subtours whose
        // CURRENT mode is in the modes list above are candidates for a
        // switch. car_passenger and bike are not listed, so their legs are
        // never converted to another mode, and no agent can switch onto
        // them either (they are not offered target modes).
        smc.setBehavior(SubtourModeChoice.Behavior.fromSpecifiedModesToSpecifiedModes);

        // ── ChangeSingleTripMode config ────────────────────────────────────
        // Restricted to NON-chain-based modes: a single trip may only flip
        // between pt and walk. fromSpecifiedModesToSpecifiedModes means only
        // trips CURRENTLY on pt/walk are candidates — car chains stay
        // intact and bike/car_passenger stay locked.
        ChangeModeConfigGroup changeMode = emptyConfig.changeMode();
        changeMode.setModes(new String[] {"pt", "walk"});
        changeMode.setBehavior(ChangeModeConfigGroup.Behavior.fromSpecifiedModesToSpecifiedModes);

        // ── Strategy ───────────────────────────────────────────────────────
        // ReRoute 0.1 + ChangeSingleTripMode 0.05 + SubtourModeChoice 0.15
        // + ChangeExpBeta 0.7 (score-based selector, uses brainExpBeta = 2).
        // Innovation strategies disabled in last 20% of iterations;
        // ChangeExpBeta then converges agents onto their best-scoring plans.
        emptyConfig.strategy().setMaxAgentPlanMemorySize(5);
        emptyConfig.strategy().setFractionOfIterationsToDisableInnovation(0.8);

        StrategyConfigGroup.StrategySettings reRoute = new StrategyConfigGroup.StrategySettings();
        reRoute.setStrategyName(DefaultPlanStrategiesModule.DefaultStrategy.ReRoute);
        reRoute.setWeight(0.1);
        emptyConfig.strategy().addStrategySettings(reRoute);

        StrategyConfigGroup.StrategySettings changeSingleTripMode = new StrategyConfigGroup.StrategySettings();
        changeSingleTripMode.setStrategyName(DefaultPlanStrategiesModule.DefaultStrategy.ChangeSingleTripMode);
        changeSingleTripMode.setWeight(0.05);
        emptyConfig.strategy().addStrategySettings(changeSingleTripMode);

        StrategyConfigGroup.StrategySettings subtourModeChoice = new StrategyConfigGroup.StrategySettings();
        subtourModeChoice.setStrategyName(DefaultPlanStrategiesModule.DefaultStrategy.SubtourModeChoice);
        subtourModeChoice.setWeight(0.15);
        emptyConfig.strategy().addStrategySettings(subtourModeChoice);

        StrategyConfigGroup.StrategySettings changeExpBeta = new StrategyConfigGroup.StrategySettings();
        changeExpBeta.setStrategyName(DefaultPlanStrategiesModule.DefaultSelector.ChangeExpBeta);
        changeExpBeta.setWeight(0.7);
        emptyConfig.strategy().addStrategySettings(changeExpBeta);
        emptyConfig.controler().setOutputDirectory(".\\scenarios\\Input_and_outputFile\\Dticket_output");
        emptyConfig.controler().setFirstIteration(0);
        emptyConfig.controler().setLastIteration(100);
        emptyConfig.controler().setOverwriteFileSetting(
                OutputDirectoryHierarchy.OverwriteFileSetting.deleteDirectoryIfExists);
        Scenario myScenario = ScenarioUtils.loadScenario(emptyConfig);

        // Attach activities only to car-accessible links. Using the full
        // multimodal network can attach them to artificial PT links, causing
        // car trips to abort when their first road link is disconnected.
        Network carNetwork = NetworkUtils.createNetwork();
        new TransportModeNetworkFilter(myScenario.getNetwork())
                .filter(carNetwork, Sets.newHashSet(TransportMode.car));
        XY2Links carLinksForActivities = new XY2Links(carNetwork, myScenario.getActivityFacilities());
        myScenario.getPopulation().getPersons().values().forEach(carLinksForActivities::run);

        // The router only considers links whose allowedModes contain the
        // routing mode. The network file only tags "car", so open every car
        // link to car_passenger — routing then follows the car network.
        for (Link link : myScenario.getNetwork().getLinks().values()) {
            if (link.getAllowedModes().contains(TransportMode.car)) {
                Set<String> allowed = new HashSet<>(link.getAllowedModes());
                allowed.add("car_passenger");
                link.setAllowedModes(allowed);
            }
        }
        Controler myControler = new Controler(myScenario);
        // Register custom scoring + license-aware mode availability.
        //   - bindScoringFunctionFactory: VTTS/transfer scoring + flat
        //     weekly D-Ticket fare (49/4 euros if the agent uses PT)
        //   - bind(PermissibleModesCalculator): replaces MATSim's default
        //     so SubtourModeChoice only offers "car" to agents whose
        //     person attribute driving_license == true.
        myControler.addOverridingModule(new AbstractModule() {
            @Override
            public void install() {
                bindScoringFunctionFactory().to(DTicketScoringFunctionFactory.class);
                bind(PermissibleModesCalculator.class).to(LicensePermissibleModesCalculator.class);
                // Route car_passenger with the CAR mode's congested travel
                // time (TravelTimeCalculator of the previous iteration) and
                // the car disutility — same wiring MATSim uses for "ride".
                addTravelTimeBinding("car_passenger").to(networkTravelTime());
                addTravelDisutilityFactoryBinding("car_passenger").to(carTravelDisutilityFactoryKey());
            }
        });
        myControler.run();
    }
}
