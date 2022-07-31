import seakers.planning.Simulator;
import seakers.planning.SimulatorParallel;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;

public class PlannerExecParallel {
    public static void main(String[] args) throws InterruptedException {
        Map<String,String> settings = new HashMap<>();
        settings.put("crosslinkEnabled","true");
        settings.put("downlinkEnabled","true");
        settings.put("downlinkSpeedMbps","0.1");
        settings.put("cameraOnPower","0.0");
        settings.put("chargePower","5.0");
        settings.put("downlinkOnPower","0.0");
        settings.put("crosslinkOnPower","0.0");
        settings.put("chlBonusReward","9999.0");
        settings.put("maxTorque","4e-5");
        settings.put("planner","dumbMcts");
        double smartSum = 0;
        double naiveSum = 0;
        double smartCount = 0;
        double naiveCount = 0;
        int numSims = 10;
        final ExecutorService executor = Executors.newFixedThreadPool(16); // it's just an arbitrary number
        //final List<Future<Map<String,Double>>> futures = new ArrayList<>();
        List<Callable<Map<String,Double>>> callableTasks = new ArrayList<>();
        for (int i = 0; i < numSims; i++) {
//            SimulatorParallel sim = new SimulatorParallel(settings);
//            Future<Map<String,Double>> future = executor.submit(sim);
//            futures.add(future);
            callableTasks.add(new SimulatorParallel(settings));
        }
        List<Future<Map<String,Double>>> futures = executor.invokeAll(callableTasks);
        //executor.awaitTermination(5,TimeUnit.SECONDS);
        try {
            for (Future<Map<String,Double>> future : futures) {
                Map<String,Double> results = future.get();
                System.out.println("getting futures");
                smartSum += results.get("smart");
                naiveSum += results.get("naive");
                smartCount += results.get("chl count smart");
                naiveCount += results.get("chl count naive");

            }
        } catch (InterruptedException | ExecutionException e) {
            e.printStackTrace();
        }
        executor.shutdown();
        System.out.println("smart avg: "+smartSum/numSims);
        System.out.println("naive avg: "+naiveSum/numSims);
        System.out.println("smartCount avg: "+smartCount/numSims);
        System.out.println("naiveCount avg: "+naiveCount/numSims);
    }
}
