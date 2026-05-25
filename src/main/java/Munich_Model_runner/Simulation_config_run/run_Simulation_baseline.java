package Munich_Model_runner.Simulation_config_run;

import com.google.common.collect.Sets;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.contrib.bicycle.BicycleConfigGroup;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.config.groups.PlanCalcScoreConfigGroup;
import org.matsim.core.config.groups.StrategyConfigGroup;
import org.matsim.core.controler.Controler;
import org.matsim.core.controler.OutputDirectoryHierarchy;
import org.matsim.core.replanning.strategies.DefaultPlanStrategiesModule;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.core.utils.geometry.transformations.TransformationFactory;

/**
 * Baseline simulation runner — standard MATSim scoring, no custom VTTS.
 * Single day simulation (24hr).
 * Demand: pre-generated weekly XML with driving_license attribute per person.
 */
public class run_Simulation_baseline {
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
        emptyConfig.qsim().setEndTime(24 * 3600);  // single day baseline
        emptyConfig.qsim().setSnapshotPeriod(0);
        emptyConfig.qsim().setMainModes(Sets.newHashSet("car"));
        emptyConfig.planCalcScore().setLearningRate(1);
        emptyConfig.planCalcScore().setBrainExpBeta(2);
        emptyConfig.planCalcScore().setLateArrival_utils_hr(-18);
        emptyConfig.planCalcScore().setEarlyDeparture_utils_hr(0);
        emptyConfig.planCalcScore().setPerforming_utils_hr(6);
        emptyConfig.planCalcScore().setMarginalUtlOfWaiting_utils_hr(0);
        emptyConfig.plansCalcRoute().setNetworkModes(Sets.newHashSet("car"));
        emptyConfig.plansCalcRoute().setTeleportedModeSpeed("walk", 5.0 / 3.6);
        emptyConfig.plansCalcRoute().setTeleportedModeSpeed("bike", 15.0 / 3.6);
        emptyConfig.plansCalcRoute().setTeleportedModeSpeed("car_passenger", 15.0 / 3.6);
        PlanCalcScoreConfigGroup.ModeParams busParams = new PlanCalcScoreConfigGroup.ModeParams("bus");
        emptyConfig.planCalcScore().addModeParams(busParams);
        PlanCalcScoreConfigGroup.ModeParams subwayParams = new PlanCalcScoreConfigGroup.ModeParams("subway");
        emptyConfig.planCalcScore().addModeParams(subwayParams);
        PlanCalcScoreConfigGroup.ModeParams tramParams = new PlanCalcScoreConfigGroup.ModeParams("tram");
        emptyConfig.planCalcScore().addModeParams(tramParams);
        PlanCalcScoreConfigGroup.ModeParams carPassengerParams = new PlanCalcScoreConfigGroup.ModeParams("car_passenger");
        emptyConfig.planCalcScore().addModeParams(carPassengerParams);
        // ── Activity parameters ────────────────────────────────────────────
        // NOTE: work has opening/closing/latestStart times set here
        //       for the single-day baseline only.
        PlanCalcScoreConfigGroup.ActivityParams home = new PlanCalcScoreConfigGroup.ActivityParams("home");
        home.setActivityType("home");
        home.setPriority(1);
        home.setTypicalDuration(12 * 60 * 60);
        emptyConfig.planCalcScore().addActivityParams(home);
        PlanCalcScoreConfigGroup.ActivityParams work = new PlanCalcScoreConfigGroup.ActivityParams("work");
        work.setActivityType("work");
        work.setPriority(1);
        work.setTypicalDuration(8 * 60 * 60);
        work.setOpeningTime(7 * 60 * 60);
        work.setLatestStartTime(9 * 60 * 60);
        work.setEarliestEndTime(0);
        work.setClosingTime(18 * 60 * 60);
        emptyConfig.planCalcScore().addActivityParams(work);
        PlanCalcScoreConfigGroup.ActivityParams accompany = new PlanCalcScoreConfigGroup.ActivityParams("accompany");
        accompany.setActivityType("accompany");
        accompany.setTypicalDuration(3600);
        emptyConfig.planCalcScore().addActivityParams(accompany);
        PlanCalcScoreConfigGroup.ActivityParams education = new PlanCalcScoreConfigGroup.ActivityParams("education");
        education.setActivityType("education");
        education.setTypicalDuration(14400);
        emptyConfig.planCalcScore().addActivityParams(education);
        PlanCalcScoreConfigGroup.ActivityParams other = new PlanCalcScoreConfigGroup.ActivityParams("other");
        other.setActivityType("other");
        other.setTypicalDuration(7200);
        emptyConfig.planCalcScore().addActivityParams(other);
        PlanCalcScoreConfigGroup.ActivityParams recreation = new PlanCalcScoreConfigGroup.ActivityParams("recreation");
        recreation.setActivityType("recreation");
        recreation.setTypicalDuration(7200);
        emptyConfig.planCalcScore().addActivityParams(recreation);
        PlanCalcScoreConfigGroup.ActivityParams shopping = new PlanCalcScoreConfigGroup.ActivityParams("shopping");
        shopping.setActivityType("shopping");
        shopping.setTypicalDuration(3600);
        emptyConfig.planCalcScore().addActivityParams(shopping);
        PlanCalcScoreConfigGroup.ActivityParams subtour = new PlanCalcScoreConfigGroup.ActivityParams("subtour");
        subtour.setActivityType("subtour");
        subtour.setTypicalDuration(3600);
        emptyConfig.planCalcScore().addActivityParams(subtour);
        emptyConfig.strategy().setMaxAgentPlanMemorySize(5);
        emptyConfig.strategy().setFractionOfIterationsToDisableInnovation(0.8);
        StrategyConfigGroup.StrategySettings reRoute = new StrategyConfigGroup.StrategySettings();
        reRoute.setStrategyName(DefaultPlanStrategiesModule.DefaultStrategy.ReRoute);
        reRoute.setWeight(0.8);
        emptyConfig.strategy().addStrategySettings(reRoute);
        StrategyConfigGroup.StrategySettings bestScore = new StrategyConfigGroup.StrategySettings();
        bestScore.setStrategyName(DefaultPlanStrategiesModule.DefaultSelector.SelectRandom);
        bestScore.setWeight(0.2);
        emptyConfig.strategy().addStrategySettings(bestScore);
        emptyConfig.controler().setOutputDirectory(".\\scenarios\\Input_and_outputFile\\Baseline_output");
        emptyConfig.controler().setFirstIteration(0);
        emptyConfig.controler().setLastIteration(30);
        emptyConfig.controler().setOverwriteFileSetting(
                OutputDirectoryHierarchy.OverwriteFileSetting.deleteDirectoryIfExists);
        Scenario myScenario = ScenarioUtils.loadScenario(emptyConfig);
        Controler myControler = new Controler(myScenario);
        myControler.run();
    }
}
