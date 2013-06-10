package controllers;

/* General Java */
import java.io.File;
import java.io.IOException;
import java.util.*;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.charset.Charset;

/* Play Frameowork */
import play.*;
import play.mvc.*;
import play.data.*;
import play.db.ebean.*;
import models.*;
import views.html.*;

/* DateTime */
import org.joda.time.format.*;
import org.joda.time.DateTime;

/* JSOUP and JSON */
import org.jsoup.*;
import org.jsoup.Connection.Method;
import org.jsoup.nodes.*;
import org.jsoup.select.Elements;
import org.json.*;
import org.json.simple.JSONValue;
import com.google.gson.*;

/* RestFB */
import com.restfb.*;
import com.restfb.FacebookClient.*;
import com.restfb.json.*;
import com.restfb.types.Event;

/* Facebook Constants */
import static controllers.FB.*;

/**
 * The Seed class collects data from various sources on the web that will be saved to the database
 * in order to aid the scrapers.
*/
public class Utilities extends Controller {

    private static Integer counter = 0;

    private static DateTime parse_start_time(String start) { 
        return (start!=null && !start.isEmpty() && start != "null") ? new DateTime().parse(start) : null; 
    }

    private static DateTime parse_end_time(String end) { 
        return (end!=null && !end.isEmpty() && end != "null") ? new DateTime().parse(end) : null; 
    }

    private static String getCreatorOrOwner(com.restfb.json.JsonObject event) {
        String creator = "";

        if (event.has("owner")) {
            try {
                JSONObject owner = new JSONObject(event.getString("owner"));
                creator = owner.has("id") ? owner.get("id").toString() : "";
            } catch (JSONException e) {
                System.out.println(e.getMessage());
            }
        } else if (event.has("creator")) {
            creator = event.getString("creator");
        }

        return creator;
    }

    public static MyEvent createEvent(com.restfb.json.JsonObject event) {

        String str_eid = event.has("id") ? event.getString("id") : (event.has("eid") ? event.getString("eid") : "");
        Long eid = Long.valueOf(str_eid).longValue();
        String name = event.has("name") ? event.getString("name") : "";
        String creator = getCreatorOrOwner(event);
        DateTime starttime = event.has("start_time") ? parse_start_time(event.getString("start_time")) : null;
        DateTime endtime = event.has("end_time") ? parse_end_time(event.getString("end_time")) : null;
        String location = event.has("location") ? event.getString("location") : "";
        String venue = event.has("venue") ? event.getString("venue") : "";
        String description = event.has("description") ? event.getString("description") : "";
        String pic = event.has("pic") ? event.getString("pic") : "";

        if (name.length() >=255 || creator.length() >= 255 || location.length() >= 255 || venue.length() >= 255) {
            System.out.println("error start");
            System.out.println(name);
            System.out.println(creator);
            System.out.println(location);
            System.out.println(venue);
            System.out.println("error end");
        }

        return new MyEvent(eid, name, creator, starttime, endtime, location, venue, description, pic);
    }

    public static boolean saveOrUpdate(MyEvent event) {
        Long eid = event.eid;
        try {
            if (MyEvent.findLong.byId(eid)==null) { 
                event.save();
                counter++;
                System.out.println("[" + counter + "] " + event.eid + ": " + event.name);
                return true; 
            }
        } catch (Exception e) {
            System.out.println(e.getMessage());
        }

        return false;
    }

    private static com.restfb.json.JsonObject venue_location(String venueID) {
        FacebookClient facebookClient = new DefaultFacebookClient(APP_ACCESS_TOKEN);
        com.restfb.json.JsonObject result = facebookClient.fetchObject( venueID, com.restfb.json.JsonObject.class);
        com.restfb.json.JsonObject location = result.getJsonObject("location");
        return location;
    }

    public static boolean filter_event(MyEvent event) {

        JSONObject venue;
        String venue_id, venue_street, venue_city, venue_state, venue_country, venue_zip, venue_longitude, venue_latitude;
        String location_id, location_street, location_city, location_state, location_country, location_zip, location_longitude, location_latitude;

        venue_street = venue_city = venue_state = venue_country = venue_zip = venue_latitude = venue_longitude = "";
        location_street = location_city = location_state = location_country = location_zip = location_latitude = location_longitude = "";

        try {

            if ( ((event.venue).equals("")) || ((event.venue).isEmpty()) || ((event.venue).equals("[]"))) {
                // System.out.println(event.toJSONString());
                venue = null; // find some sort of filter for events with no venue?
                return false;
            }
            else {
                venue = new JSONObject(event.venue);
            }

            venue_id = venue.has("id") ? venue.get("id").toString() : "";

            boolean blocked_venue = Arrays.asList(BLOCKED_VENUES).contains(venue_id);
            boolean blocked_organization = Arrays.asList(BLOCKED_ORGANIZATIONS).contains(event.creator);
            if (blocked_venue || blocked_organization) return false;

            venue_street = venue.has("street") ? venue.get("street").toString() : "";
            venue_city = venue.has("city") ? venue.get("city").toString() : "";
            venue_state = venue.has("state") ? venue.get("state").toString() : "";
            venue_country = venue.has("country") ? venue.get("country").toString() : "";
            venue_zip = venue.has("zip") ? venue.get("zip").toString() : "";
            venue_longitude = venue.has("latitude") ? venue.get("latitude").toString() : "";
            venue_latitude = venue.has("longitude") ? venue.get("longitude").toString() : "";
            
            if (!venue_id.equals("")) {
                com.restfb.json.JsonObject location = venue_location(venue_id);
                location_street = location.has("street") ? location.get("street").toString() : "";
                location_city = location.has("city") ? location.get("city").toString() : "";
                location_state = location.has("state") ? location.get("state").toString() : "";
                location_country = location.has("country") ? location.get("country").toString() : "";
                location_zip = location.has("zip") ? location.get("zip").toString() : "";
                location_latitude = location.has("latitude") ? location.get("latitude").toString() : "";
                location_longitude = location.has("longitude") ? location.get("longitude").toString() : "";
            }

            String street = (!venue_street.equals("")) ? venue_street : location_street;
            String city = (!venue_city.equals("")) ? venue_city : location_city;
            String state = (!venue_state.equals("")) ? venue_state : location_state;
            String country = (!venue_country.equals("")) ? venue_country : location_country;
            String zip = (!venue_zip.equals("")) ? venue_zip : location_zip;
            String latitude = (!venue_longitude.equals("")) ? venue_longitude : location_country;
            String longitude = (!venue_latitude.equals("")) ? venue_latitude : location_country;

            // if(latitude,longitude is less than 1500 meters from center of NU) return true; // TEST 1: longitude, latitude
            if (city.equalsIgnoreCase("evanston") || city.equalsIgnoreCase("chicago")) return true; // TEST 2: city
            // if (state.equalsIgnoreCase("Illinois")) return true; // TEST 3: state
            // if (country.equalsIgnoreCase("United States")) return true; // TEST 4: country

        } catch (JSONException e) {
            System.out.println(e.getMessage());
        }

        return false;
    }

    public static List<MyEvent> save_events(List<com.restfb.json.JsonObject> events) {
        List<MyEvent> eventList = new ArrayList<MyEvent>();

        for (com.restfb.json.JsonObject event : events) {
            MyEvent newEvent = Utilities.createEvent(event);
            if (Utilities.filter_event(newEvent) && Utilities.saveOrUpdate(newEvent)) { 
                eventList.add(newEvent); 
            }
        }

        return eventList;
    }

    public static List<MyEvent> save_events(com.restfb.json.JsonArray events) {
        List<MyEvent> eventList = new ArrayList<MyEvent>();

        for(int i = 0; i < events.length(); i++) {
            com.restfb.json.JsonObject event = (com.restfb.json.JsonObject) events.get(i);
            MyEvent newEvent = Utilities.createEvent(event);
            if (Utilities.filter_event(newEvent) && Utilities.saveOrUpdate(newEvent)) {
                eventList.add(newEvent);
            }
        }

        return eventList;
    }

}