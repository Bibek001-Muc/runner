package Munich_Model_runner.Simulation_config_run;

import com.google.common.collect.Sets;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.contrib.bicycle.BicycleConfigGroup;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.config.groups.PlanCalcScoreConfigGroup;
import org.matsim.core.config.groups.StrategyConfigGroup;
import org.matsim.core.config.groups.SubtourModeChoiceConfigGroup;
import org.matsim.core.controler.AbstractModule;
import org.matsim.core.controler.Controler;
import org.matsim.core.controler.OutputDirectoryHierarchy;
import org.matsim.core.population.algorithms.PermissibleModesCalculator;
import org.matsim.core.replanning.strategies.DefaultPlanStrategiesModule;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.core.utils.geometry.transformations.TransformationFactory;
import Munich_Model_runner.scoring.CustomScoringFunctionFactory;
import Munich_Model_runner.replanning.LicensePermissibleModesCalculator;

/**
 * Custom simulation runner — 7-day weekly plans with:
 *   - Activity-type-specific VTTS (postintervention, Table 6)
 *   - Weekly MVV zonal PT fare deducted once per plan
 *   - β_money = 1, β_transfer = 1 euro/transfer
 *   - PT wait VTTS = 7.16 euros/hr
 *   - Car operating cost = 0.65 euros/km (all-in)
 *
 * Work activity: NO opening/closing/latestStart time constraints.
 * Reason: demand is pre-generated weekly — MATSim must not penalise
 * agents whose work activities fall outside a single-day time window.
 */
public class run_Simulation_custom {
    public static void main(String[] args) {
        Config emptyConfig = ConfigUtils.createConfig();
        emptyConfig.global().setRandomSeed(4711);
        emptyConfig.global().setCoordinateSystem(TransformationFactory.GK4);
        emptyConfig.addModule(new BicycleConfigGroup());
        emptyConfig.plans().setInputFile(".\\scenarios\\Input_and_outputFile\\Other_input_and_output_file\\DemandTest06.xml");
        emptyConfig.network().setInputFile(".\\scenarios\\Input_and_outputFile\\Other_input_and_output_file\\mapped_network_baseline.xml");
        emptyConfig.transit().setUseTransit(true);
        emptyConfig.transit().setTransitScheduleFile(".\\scenarios\\Input_and_outputFile\\Other_input_and_output_file\\munichschedulemapped.xml");
        emptyConfig.transit().setVehiclesFile(".\\scenarios\\Input_and_outputFile\\Other_input_and_output_file\\MunichVehicles.xml");
        emptyConfig.transit().setTransitModes(Sets.newHashSet(TransportMode.pt));
        emptyConfig.qsim().setFlowCapFactor(0.05);
        emptyConfig.qsim().setStorageCapFactor(0.05);
        emptyConfig.qsim().setStartTime(0);
        emptyConfig.qsim().setEndTime(7 * 24 * 3600); // 7-day weekly simulation
        emptyConfig.qsim().setSnapshotPeriod(0);
        emptyConfig.qsim().setMainModes(Sets.newHashSet("car"));
        // ── Scoring globals ────────────────────────────────────────────────
        emptyConfig.planCalcScore().setLearningRate(1);
        emptyConfig.planCalcScore().setBrainExpBeta(2);
        emptyConfig.planCalcScore().setLateArrival_utils_hr(-18);
        emptyConfig.planCalcScore().setEarlyDeparture_utils_hr(0);
        emptyConfig.planCalcScore().setPerforming_utils_hr(6);
        emptyConfig.planCalcScore().setMarginalUtilityOfMoney(1.0);           // β_money = 1
        emptyConfig.planCalcScore().setMarginalUtlOfWaiting_utils_hr(-7.16); // PT wait VTTS
        emptyConfig.plansCalcRoute().setNetworkModes(Sets.newHashSet("car"));
        emptyConfig.plansCalcRoute().setTeleportedModeSpeed("walk", 5.0 / 3.6);
        emptyConfig.plansCalcRoute().setTeleportedModeSpeed("bike", 15.0 / 3.6);
        emptyConfig.plansCalcRoute().setTeleportedModeSpeed("car_passenger", 15.0 / 3.6);
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
        // Agents can switch among {car, pt, bike, walk} on a per-subtour basis.
        // chainBasedModes = {car, bike} → the mode of a subtour must return
        //                   to its origin (you can't leave car/bike at work).
        // considerCarAvailability = true → MATSim consults the bound
        //                   PermissibleModesCalculator (our custom one below)
        //                   to filter modes per agent. Agents without
        //                   driving_license cannot pick "car".
        SubtourModeChoiceConfigGroup smc = emptyConfig.subtourModeChoice();
        smc.setModes(new String[] {"car", "pt", "bike", "walk"});
        smc.setChainBasedModes(new String[] {"car", "bike"});
        smc.setConsiderCarAvailability(true);

        // ── Strategy ───────────────────────────────────────────────────────
        // ReRoute 0.6  + SubtourModeChoice 0.2  + SelectRandom 0.2
        // Innovation (ReRoute + SubtourModeChoice) disabled in last 20% of
        // iterations so agents converge on their best-scoring plan.
        emptyConfig.strategy().setMaxAgentPlanMemorySize(5);
        emptyConfig.strategy().setFractionOfIterationsToDisableInnovation(0.8);

        StrategyConfigGroup.StrategySettings reRoute = new StrategyConfigGroup.StrategySettings();
        reRoute.setStrategyName(DefaultPlanStrategiesModule.DefaultStrategy.ReRoute);
        reRoute.setWeight(0.6);
        emptyConfig.strategy().addStrategySettings(reRoute);

        StrategyConfigGroup.StrategySettings subtourModeChoice = new StrategyConfigGroup.StrategySettings();
        subtourModeChoice.setStrategyName(DefaultPlanStrategiesModule.DefaultStrategy.SubtourModeChoice);
        subtourModeChoice.setWeight(0.2);
        emptyConfig.strategy().addStrategySettings(subtourModeChoice);

        StrategyConfigGroup.StrategySettings selectRandom = new StrategyConfigGroup.StrategySettings();
        selectRandom.setStrategyName(DefaultPlanStrategiesModule.DefaultSelector.SelectRandom);
        selectRandom.setWeight(0.2);
        emptyConfig.strategy().addStrategySettings(selectRandom);
        emptyConfig.controler().setOutputDirectory(".\\scenarios\\Input_and_outputFile\\Custom_output");
        emptyConfig.controler().setFirstIteration(0);
        emptyConfig.controler().setLastIteration(30);
        emptyConfig.controler().setOverwriteFileSetting(
                OutputDirectoryHierarchy.OverwriteFileSetting.deleteDirectoryIfExists);
        Scenario myScenario = ScenarioUtils.loadScenario(emptyConfig);
        Controler myControler = new Controler(myScenario);
        // Register custom scoring + license-aware mode availability.
        //   - bindScoringFunctionFactory: our VTTS/fare/transfer scoring
        //   - bind(PermissibleModesCalculator): replaces MATSim's default
        //     so SubtourModeChoice only offers "car" to agents whose
        //     person attribute driving_license == true.
        myControler.addOverridingModule(new AbstractModule() {
            @Override
            public void install() {
                bindScoringFunctionFactory().to(CustomScoringFunctionFactory.class);
                bind(PermissibleModesCalculator.class).to(LicensePermissibleModesCalculator.class);
            }
        });
        myControler.run();
    }
}
