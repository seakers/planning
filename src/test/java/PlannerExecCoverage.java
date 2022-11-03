import org.orekit.bodies.GeodeticPoint;
import seakers.planning.EqualSimulator;
import seakers.planning.PlannerCoverageMetrics;
import seakers.planning.Simulator;

import java.util.HashMap;
import java.util.Map;

public class PlannerExecCoverage {
    public static void main(String[] args) {
        Map<String,String> settings = new HashMap<>();
        settings.put("crosslinkEnabled","true");
        settings.put("downlinkEnabled","true");
        settings.put("downlinkSpeedMbps","1000.1");
        settings.put("cameraOnPower","0.0");
        settings.put("chargePower","5.0");
        settings.put("downlinkOnPower","0.0");
        settings.put("crosslinkOnPower","0.0");
        settings.put("chlBonusReward","100.0");
        settings.put("maxTorque","4e-5");
        settings.put("planner","ruleBased");
        settings.put("resources","false");
        EqualSimulator simulator = new EqualSimulator(settings);
        Map<String,Map<GeodeticPoint,Double[]>> plannerAccesses = simulator.getPlannerAccesses();
        PlannerCoverageMetrics pcm = new PlannerCoverageMetrics("./src/test/resources/plannerData/oneday",plannerAccesses);
    }
}
