package seakers.planning;

import org.apache.commons.math3.util.FastMath;
import org.apache.http.HttpEntity;
import org.apache.http.NameValuePair;
import org.apache.http.ParseException;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.util.EntityUtils;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.orekit.bodies.GeodeticPoint;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.util.*;

import static java.lang.Double.parseDouble;

public class TimeVaryingEventNaivePlanExecutor {
    private String satelliteName;
    private ArrayList<SatelliteAction> actionsTaken;
    private boolean doneFlag;
    private Map<GeodeticPoint, EventObservation> rewardGridUpdates;
    private double imageProcessingTime;
    private double rewardDownlinked;
    Map<GeodeticPoint,Double> geophysicalLimits = new HashMap<>();
    Map<GeodeticPoint,Double> currentGeophysical = new HashMap<>();
    private ArrayList<EventObservation> storedEventObservations = new ArrayList<>();
    private ArrayList<EventObservation> downlinkedEventObservations = new ArrayList<>();
    private Map<GeodeticPoint,GeophysicalEvent> eventGrid;
    Map<String,String> settings;

    public TimeVaryingEventNaivePlanExecutor(SatelliteState s, double startTime, double endTime, ArrayList<SatelliteAction> actionsToTake, String satelliteName, Map<GeodeticPoint,GeophysicalEvent> eventGrid, Map<String,String> settings) {
        doneFlag = false;
        imageProcessingTime = 0.0;
        rewardDownlinked = 0.0;
        rewardGridUpdates = new HashMap<>();
        actionsTaken = new ArrayList<>();
        this.satelliteName = satelliteName;
        this.settings = settings;
        this.eventGrid = eventGrid;
        double currentTime = startTime;
        loadGeophysical();
        while(!doneFlag) {
            SatelliteAction actionToTake = null;
            for(SatelliteAction a : actionsToTake) {
                if(a.gettStart() > currentTime && a.gettStart() < endTime) {
                    actionToTake = a;
                    break;
                }
            }
            if(actionToTake == null) {
                break;
            }
            actionsTaken.add(actionToTake);
            s = transitionFunction(s,actionToTake);
            currentTime = s.getT();
            if(currentTime > endTime) {
                doneFlag = true;
            }
        }
    }
    public SatelliteState transitionFunction(SatelliteState s, SatelliteAction a) {
        double t = a.gettEnd();
        double tPrevious = s.getT();
        ArrayList<GeophysicalEvent> satGeophysicalEvents = new ArrayList<>(s.getGeophysicalEvents());
        ArrayList<EventObservation> satEventObservations = new ArrayList<>(s.getEventObservations());
        ArrayList<String> currentCrosslinkLog = new ArrayList<>(s.getCrosslinkLog());
        ArrayList<String> currentDownlinkLog = new ArrayList<>(s.getDownlinkLog());
        ArrayList<SatelliteAction> history = new ArrayList<>(s.getHistory());
        history.add(a);
        double storedImageReward = s.getStoredImageReward();
        double batteryCharge = s.getBatteryCharge();
        double dataStored = s.getDataStored();
        double currentAngle = s.getCurrentAngle();
        switch (a.getActionType()) {
            case "charge":
                batteryCharge = batteryCharge + (a.gettEnd() - s.getT()) * Double.parseDouble(settings.get("chargePower")) / 3600; // Wh
                break;
            case "imaging":
                batteryCharge = batteryCharge + (a.gettStart() - s.getT()) * Double.parseDouble(settings.get("chargePower")) / 3600;
                batteryCharge = batteryCharge - (a.gettEnd() - a.gettStart()) * Double.parseDouble(settings.get("cameraOnPower")) / 3600;
                dataStored = dataStored + 1.0;
                currentAngle = a.getAngle();
                storedImageReward = storedImageReward + 0.0;
                boolean interestingImage = processImage(a.gettStart(), a.getLocation());
                if (interestingImage) {
                    satEventObservations.addAll(storedEventObservations);
                    storedImageReward = storedImageReward + Double.parseDouble(settings.get("chlBonusReward"));
                }
                break;
            case "downlink":
                batteryCharge = batteryCharge + (a.gettStart() - s.getT()) * Double.parseDouble(settings.get("chargePower")) / 3600;
                batteryCharge = batteryCharge - (a.gettEnd() - a.gettStart()) * Double.parseDouble(settings.get("downlinkOnPower")) / 3600;
                double dataFracDownlinked = ((a.gettEnd() - a.gettStart()) * Double.parseDouble(settings.get("downlinkSpeedMbps"))) / dataStored; // data is in Mb, 0.1 Mbps
                dataStored = dataStored - (a.gettEnd() - a.gettStart()) * Double.parseDouble(settings.get("downlinkSpeedMbps"));
                if (dataStored < 0) {
                    dataStored = 0;
                    dataFracDownlinked = 1.0;
                }
                rewardDownlinked += storedImageReward;
                storedImageReward = 0.0;
//                rewardDownlinked += storedImageReward * dataFracDownlinked;
//                storedImageReward = storedImageReward - storedImageReward * dataFracDownlinked;
//                if(storedImageReward < 0) {
//                    storedImageReward = 0;
//                }
                currentDownlinkLog.add("Downlink from time " + a.gettStart() + " to time " + a.gettEnd());
                downlinkedEventObservations.addAll(storedEventObservations);
                storedEventObservations.clear();
                break;
        }
        return new SatelliteState(t,tPrevious,history,batteryCharge,dataStored,currentAngle,storedImageReward,satGeophysicalEvents,satEventObservations,currentCrosslinkLog,currentDownlinkLog);
    }

    public Map<GeodeticPoint, EventObservation> getRewardGridUpdates() {
        return rewardGridUpdates;
    }

    public void loadGeophysical() {
        List<List<String>> geophysicalBaselines = new ArrayList<>();
        List<List<String>> geophysicalRecents = new ArrayList<>();
        try (BufferedReader br = new BufferedReader(new FileReader("./src/test/resources/chlorophyll_baseline.csv"))) {
            String line;
            int count = 0;
            while ((line = br.readLine()) != null) {
                if(count == 0) {
                    count++;
                    continue;
                }
                String[] values = line.split(",");
                geophysicalBaselines.add(Arrays.asList(values));
            }
        } catch (Exception e) {
            System.out.println("Exception occurred in loadCoveragePoints: " + e);
        }
        for (List<String> geophysicalBaseline : geophysicalBaselines) {
            double lon = Math.toRadians(parseDouble(geophysicalBaseline.get(0)));
            double lat = Math.toRadians(parseDouble(geophysicalBaseline.get(1)));
            double mean = parseDouble(geophysicalBaseline.get(2));
            double sd = parseDouble(geophysicalBaseline.get(3));
            GeodeticPoint chloroPoint = new GeodeticPoint(lat, lon, 0.0);
            geophysicalLimits.put(chloroPoint, mean + sd);
        }
        try (BufferedReader br = new BufferedReader(new FileReader("./src/test/resources/chlorophyll_recent.csv"))) {
            String line;
            while ((line = br.readLine()) != null) {
                String[] values = line.split(",");
                geophysicalRecents.add(Arrays.asList(values));
            }
        } catch (Exception e) {
            System.out.println("Exception occurred in loadCoveragePoints: " + e);
        }
        for (int i = 0; i < geophysicalRecents.size(); i++) {
            double lon = Math.toRadians(parseDouble(geophysicalBaselines.get(i).get(0)));
            double lat = Math.toRadians(parseDouble(geophysicalBaselines.get(i).get(1)));
            double chl = parseDouble(geophysicalRecents.get(i).get(2));
            GeodeticPoint chloroPoint = new GeodeticPoint(lat, lon, 0.0);
            currentGeophysical.put(chloroPoint, chl);
        }
    }

    public boolean processImage(double time, GeodeticPoint location) {
        if(eventGrid.containsKey(location)) {
            if(eventGrid.get(location).getStartTime() < time && time < eventGrid.get(location).getEndTime()) {
                EventObservation eventObservation = new EventObservation(location,time,eventGrid.get(location).getValue());
                rewardGridUpdates.put(location,eventObservation);
                storedEventObservations.add(eventObservation);
                return true;
            } else {
                return false;
            }
        } else {
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
            System.out.println("Geophysical outlier at: "+location+", BDA: "+bda);
//            rewardGridUpdates.put(location,100.0);
            return true;
        } else {
            return false;
        }
    }

    public double getRewardDownlinked() { return rewardDownlinked; }

    public ArrayList<SatelliteAction> getActionsTaken() {
        return actionsTaken;
    }

    public ArrayList<EventObservation> getEventObservations() { return downlinkedEventObservations; }
}

