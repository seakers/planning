import org.orekit.bodies.GeodeticPoint;
import seakers.planning.EqualSimulator;
import seakers.planning.PlannerCoverageMetrics;
import seakers.planning.Simulator;

import java.util.HashMap;
import java.util.Map;

public class PlannerExecCoverage {
    public static void main(String[] args) {
        double maxTorque = 4e-10;
        for(int i = 0; i < 10; i++) {
            Map<String,String> settings = new HashMap<>();
            settings.put("crosslinkEnabled","true");
            settings.put("downlinkEnabled","true");
            settings.put("downlinkSpeedMbps","1000.1");
            settings.put("cameraOnPower","0.0");
            settings.put("chargePower","5.0");
            settings.put("downlinkOnPower","0.0");
            settings.put("crosslinkOnPower","0.0");
            settings.put("chlBonusReward","100.0");
            settings.put("maxTorque",Double.toString(maxTorque));
            settings.put("planner","ruleBased");
            settings.put("resources","false");
            String filepath = "./src/test/resources/plannerData/thirtydays_sixteensats_45deg";
            EqualSimulator simulator = new EqualSimulator(settings,filepath);
            Map<String,Map<GeodeticPoint,Double[]>> plannerAccesses = simulator.getPlannerAccesses();
            PlannerCoverageMetrics pcm = new PlannerCoverageMetrics(filepath,plannerAccesses);
            maxTorque *= 10;
        }
    }
}
