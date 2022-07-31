package seakers.planning;

import org.apache.commons.math3.util.FastMath;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.HttpEntity;
import org.apache.http.NameValuePair;
import org.apache.http.ParseException;
import org.apache.http.util.EntityUtils;
import org.apache.http.message.BasicNameValuePair;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.orekit.bodies.GeodeticPoint;
import seakers.orekit.coverage.access.TimeIntervalArray;
import seakers.planning.SatelliteAction;
import seakers.planning.SatelliteState;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.util.*;

import static java.lang.Double.NaN;
import static java.lang.Double.parseDouble;

public class PlanExecutor {
    private double stopTime;
    private String replanFlag;
    private String satelliteName;
    private ArrayList<SatelliteAction> actionsTaken;
    private boolean doneFlag;
    private SatelliteState returnState;
    private double rewardDownlinked;
    private Map<GeodeticPoint, ChlorophyllEvent> rewardGridUpdates;
    private double imageProcessingTime;
    Map<GeodeticPoint,Double> chlorophyllLimits = new HashMap<>();
    Map<GeodeticPoint,Double> currentChlorophyll = new HashMap<>();
    private ArrayList<ChlorophyllEvent> chlorophyllEvents = new ArrayList<>();
    private Map<String,String> settings;

    public PlanExecutor(SatelliteState s, double startTime, double endTime, ArrayList<SatelliteAction> actionsToTake, String satelliteName, Map<String,String> settings) {
        doneFlag = false;
        imageProcessingTime = 0.0;
        rewardDownlinked = 0.0;
        rewardGridUpdates = new HashMap<>();
        actionsTaken = new ArrayList<>();
        replanFlag = "";
        this.settings = settings;
        this.satelliteName = satelliteName;
        double currentTime = startTime;
        loadChlorophyll();
        while(!doneFlag) {
            SatelliteAction actionToTake = null;
            for(SatelliteAction a : actionsToTake) {
                if(a.gettStart() > currentTime && a.gettStart() < endTime) {
                    actionToTake = a;
                    break;
                }
            }
            if(actionToTake == null) {
                stopTime = endTime;
                returnState = s;
                break;
            }
            actionsTaken.add(actionToTake);
            s = transitionFunction(s,actionToTake);
            returnState = s;
            currentTime = s.getT();
            if(currentTime > endTime) {
                doneFlag = true;
                stopTime = currentTime;
            }
        }
    }
    public SatelliteState transitionFunction(SatelliteState s, SatelliteAction a) {
        double t = a.gettEnd();
        double tPrevious = s.getT();
        ArrayList<ChlorophyllEvent> satChlorophyllEvents = new ArrayList<>(s.getChlorophyllEvents());
        ArrayList<String> currentCrosslinkLog = new ArrayList<>(s.getCrosslinkLog());
        ArrayList<String> currentDownlinkLog = new ArrayList<>(s.getDownlinkLog());
        ArrayList<SatelliteAction> history = new ArrayList<>(s.getHistory());
        history.add(a);
        double storedImageReward = s.getStoredImageReward();
        double batteryCharge = s.getBatteryCharge();
        double dataStored = s.getDataStored();
        //System.out.println(satelliteName+" data stored: "+dataStored);
        double currentAngle = s.getCurrentAngle();
        switch (a.getActionType()) {
            case "charge" -> batteryCharge = batteryCharge + (a.gettEnd() - s.getT()) * Double.parseDouble(settings.get("chargePower")) / 3600; // Wh
            case "imaging" -> {
                batteryCharge = batteryCharge + (a.gettStart() - s.getT()) * Double.parseDouble(settings.get("chargePower")) / 3600;
                batteryCharge = batteryCharge - (a.gettEnd() - a.gettStart()) * Double.parseDouble(settings.get("cameraOnPower")) / 3600;
                dataStored = dataStored + 1.0;
                currentAngle = a.getAngle();
                storedImageReward = storedImageReward + 1.0;
                boolean interestingImage = processImage(a.gettStart(), a.getLocation(), satelliteName);
                if (interestingImage) {
                    satChlorophyllEvents.addAll(chlorophyllEvents);
                    storedImageReward = storedImageReward + Double.parseDouble(settings.get("chlBonusReward"));
                    stopTime = a.gettEnd();
                    replanFlag = "image";
                    doneFlag = true;
                }
            }
            case "downlink" -> {
                batteryCharge = batteryCharge + (a.gettStart() - s.getT()) * Double.parseDouble(settings.get("chargePower")) / 3600;
                batteryCharge = batteryCharge - (a.gettEnd() - a.gettStart()) * Double.parseDouble(settings.get("downlinkOnPower")) / 3600;
                double dataFracDownlinked = ((a.gettEnd() - a.gettStart()) * Double.parseDouble(settings.get("downlinkSpeedMbps"))) / dataStored; // data is in Mb, 0.1 Mbps
                dataStored = dataStored - (a.gettEnd() - a.gettStart()) * Double.parseDouble(settings.get("downlinkSpeedMbps"));
                if (dataStored < 0) {
                    dataStored = 0;
                    dataFracDownlinked = 1.0;
                }
                //rewardDownlinked += storedImageReward * dataFracDownlinked;
                rewardDownlinked += storedImageReward;
                storedImageReward = 0;
//                storedImageReward = storedImageReward - storedImageReward * dataFracDownlinked;
//                if(storedImageReward < 0) {
//                    storedImageReward = 0;
//                }
                currentDownlinkLog.add("Downlink from time " + a.gettStart() + " to time " + a.gettEnd());
                stopTime = a.gettEnd();
                replanFlag = "downlink";
                doneFlag = true;
            }
        }
        return new SatelliteState(t,tPrevious,history,batteryCharge,dataStored,currentAngle,storedImageReward,satChlorophyllEvents,currentCrosslinkLog,currentDownlinkLog);
    }

    public Map<GeodeticPoint, ChlorophyllEvent> getRewardGridUpdates() {
        return rewardGridUpdates;
    }

    public void loadChlorophyll() {
        List<List<String>> chlorophyllBaselines = new ArrayList<>();
        List<List<String>> chlorophyllRecents = new ArrayList<>();
        try (BufferedReader br = new BufferedReader(new FileReader("./src/test/resources/chlorophyll_baseline.csv"))) {
            String line;
            int count = 0;
            while ((line = br.readLine()) != null) {
                if(count == 0) {
                    count++;
                    continue;
                }
                String[] values = line.split(",");
                chlorophyllBaselines.add(Arrays.asList(values));
            }
        } catch (Exception e) {
            System.out.println("Exception occurred in loadCoveragePoints: " + e);
        }
        for (List<String> chlorophyllBaseline : chlorophyllBaselines) {
            double lon = Math.toRadians(parseDouble(chlorophyllBaseline.get(0)));
            double lat = Math.toRadians(parseDouble(chlorophyllBaseline.get(1)));
            double mean = parseDouble(chlorophyllBaseline.get(2));
            double sd = parseDouble(chlorophyllBaseline.get(3));
            GeodeticPoint chloroPoint = new GeodeticPoint(lat, lon, 0.0);
            chlorophyllLimits.put(chloroPoint, mean + sd);
        }
        try (BufferedReader br = new BufferedReader(new FileReader("./src/test/resources/chlorophyll_recent.csv"))) {
            String line;
            while ((line = br.readLine()) != null) {
                String[] values = line.split(",");
                chlorophyllRecents.add(Arrays.asList(values));
            }
        } catch (Exception e) {
            System.out.println("Exception occurred in loadCoveragePoints: " + e);
        }
        for (int i = 0; i < chlorophyllRecents.size(); i++) {
            double lon = Math.toRadians(parseDouble(chlorophyllBaselines.get(i).get(0)));
            double lat = Math.toRadians(parseDouble(chlorophyllBaselines.get(i).get(1)));
            double chl = parseDouble(chlorophyllRecents.get(i).get(2));
            GeodeticPoint chloroPoint = new GeodeticPoint(lat, lon, 0.0);
            currentChlorophyll.put(chloroPoint, chl);
        }
    }
    
    public boolean processImage(double time, GeodeticPoint location, String satelliteName) {
        double limit = 0;
        double current = 0;
        for(GeodeticPoint gp : chlorophyllLimits.keySet()) {
            if(Math.sqrt(Math.pow(location.getLatitude()-gp.getLatitude(),2)+Math.pow(location.getLongitude()-gp.getLongitude(),2)) < 0.00001) {
                limit = chlorophyllLimits.get(gp);
                current = currentChlorophyll.get(gp);
                break;
            }
        }
        if(current > limit) {
            ChlorophyllEvent algalBloom = new ChlorophyllEvent(location, time, time+7200.0, limit, current);
            algalBloom.addToEventLog("Algal bloom image capture at "+location+" at "+time+" with current value "+current+" over the limit of "+limit+" by satellite "+satelliteName);
            chlorophyllEvents.add(algalBloom);
            rewardGridUpdates.put(location,algalBloom);
            return true;
        }
        else {
            return false;
        }
    }

    public boolean processImageLive(double time, GeodeticPoint location) {
        long start = System.nanoTime();
        CloseableHttpClient client = HttpClients.createDefault();
        HttpPost httpPost = new HttpPost("http://localhost:9020");

        List<NameValuePair> locationParams = new ArrayList<NameValuePair>();
        locationParams.add(new BasicNameValuePair("lat", Double.toString(FastMath.toDegrees(location.getLatitude()))));
        locationParams.add(new BasicNameValuePair("lon", Double.toString(FastMath.toDegrees(location.getLongitude()))));
        try {
            httpPost.setEntity(new UrlEncodedFormEntity(locationParams));
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }
        JSONObject radarResult = new JSONObject();
        CloseableHttpResponse response = null;
        String answer = null;
        double bda = 0.0;
        try {
            response = client.execute(httpPost);
            HttpEntity entity = response.getEntity();
            String jsonString = EntityUtils.toString(entity, StandardCharsets.UTF_8);
            JSONParser parser = new JSONParser();
            radarResult = (JSONObject) parser.parse(jsonString);
            bda = (double) radarResult.get("bda");
            answer = (String) radarResult.get("flag");
            client.close();
        } catch (IOException | ParseException | org.json.simple.parser.ParseException e) {
            e.printStackTrace();
        }
        long end = System.nanoTime();
        double elapsed = (end-start)/1e9;
        imageProcessingTime = imageProcessingTime + elapsed;
        if(answer.equals("outlier")) {
            System.out.println("Chlorophyll outlier at: "+location+", BDA: "+bda);
//            rewardGridUpdates.put(location,100.0);
            return true;
        } else {
            return false;
        }
    }

    public double getRewardDownlinked() { return rewardDownlinked; }

    public double getStopTime() {
        return stopTime;
    }

    public String getReplanFlag() {
        return replanFlag;
    }

    public ArrayList<SatelliteAction> getActionsTaken() {
        return actionsTaken;
    }

    public SatelliteState getReturnState() {
        return returnState;
    }

    public ArrayList<ChlorophyllEvent> getChlorophyllEvents() { return chlorophyllEvents; }
}
