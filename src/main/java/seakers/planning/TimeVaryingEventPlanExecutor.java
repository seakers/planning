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

public class TimeVaryingEventPlanExecutor {
    private double stopTime;
    private String replanFlag;
    private String satelliteName;
    private ArrayList<SatelliteAction> actionsTaken;
    private boolean doneFlag;
    private SatelliteState returnState;
    private double rewardDownlinked;
    private Map<GeodeticPoint, EventObservation> rewardGridUpdates;
    Map<GeodeticPoint,Double> geophysicalLimits = new HashMap<>();
    Map<GeodeticPoint,Double> currentGeophysical = new HashMap<>();
    private ArrayList<EventObservation> eventObservations = new ArrayList<>();

    private Map<GeodeticPoint,GeophysicalEvent> eventGrid;
    private Map<String,String> settings;

    public TimeVaryingEventPlanExecutor(SatelliteState s, double startTime, double endTime, ArrayList<SatelliteAction> actionsToTake, String satelliteName, Map<GeodeticPoint, GeophysicalEvent> eventGrid, Map<String,String> settings) {
        doneFlag = false;
        rewardDownlinked = 0.0;
        rewardGridUpdates = new HashMap<>();
        actionsTaken = new ArrayList<>();
        replanFlag = "";
        this.settings = settings;
        this.satelliteName = satelliteName;
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
        ArrayList<GeophysicalEvent> satGeophysicalEvents = new ArrayList<>(s.getGeophysicalEvents());
        ArrayList<EventObservation> satEventObservations = new ArrayList<>(s.getEventObservations());
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
                storedImageReward = storedImageReward + 0.0;
                boolean interestingImage = processImage(a.gettStart(), a.getLocation());
                if (interestingImage) {
                    satEventObservations.addAll(eventObservations);
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
                eventObservations.add(eventObservation);
                return true;
            } else {
                return false;
            }
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

    public ArrayList<EventObservation> getEventObservations() { return eventObservations; }
}
