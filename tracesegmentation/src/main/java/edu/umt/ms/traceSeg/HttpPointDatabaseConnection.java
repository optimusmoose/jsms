package edu.umt.ms.traceSeg;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.apache.http.HttpEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;

import java.io.IOException;
import java.net.URI;
import java.util.List;
import java.util.ArrayList;

/**
 * Implements PointDatabaseConnection over HTTP for use with MsDataServer or a compatible interface
 * Uses API methods filestatus, gethighestuntraced, getnextids, and updatesegmentation
 * to perform segmentation operations
 */
class HttpPointDatabaseConnection implements PointDatabaseConnection {
    private URI serverRoot;
    private CloseableHttpClient httpClient = HttpClients.createDefault();

    public HttpPointDatabaseConnection(URI serverRoot) {
        this.serverRoot = serverRoot;
    }

    private String API_PATH = "/api/v2/";

    /**
     * Simplified call using apache's http tools to get JSON from an API endpoint
     * @param endpoint the name of the API (for example "filestatus")
     * @return the server response, as a JsonElement
     * @throws IOException
     */
    private JsonElement doHttpGet(String endpoint) throws IOException {
        URI uri = serverRoot.resolve(API_PATH).resolve(endpoint);
        HttpGet request = new HttpGet(uri);
        try (CloseableHttpResponse response = httpClient.execute(request)) {
            String responseText = EntityUtils.toString(response.getEntity());
            Gson gson = new Gson();
            return gson.fromJson(responseText, JsonElement.class);
        }
    }

    private JsonObject _filestatus;
    private JsonObject getFileStatus() throws Exception {
        if (_filestatus != null) { return _filestatus; }
        JsonElement filestatusE = doHttpGet("filestatus");
        if (filestatusE.isJsonObject()) {
            _filestatus = filestatusE.getAsJsonObject();
            return _filestatus;
        }
        throw new Exception("Expected filestatus to return a JSON object");
    }

    public List<Point> getAllPoints(double minMz, double maxMz, float minRt, float maxRt, double minIntensity) throws Exception{
      //PlaceHolder
      ArrayList<Point> a = new ArrayList<Point>();
      return a;
    }
    public void deleteTraces() throws Exception{
      //PlaceHolder
    }

    public double getMinimumIntensity() throws Exception {
        JsonObject filestatus = getFileStatus();
        JsonElement intMinE = filestatus.get("intmin");
        if (intMinE.isJsonPrimitive()) {
            return intMinE.getAsDouble();
        } else {
            throw new NumberFormatException("Expected intmin in filestatus to be a number");
        }
    }

    public double getMaximumIntensity() throws Exception {
        JsonObject filestatus = getFileStatus();
        JsonElement intMaxE = filestatus.get("intmax");
        if (intMaxE.isJsonPrimitive()) {
            return intMaxE.getAsDouble();
        } else {
            throw new NumberFormatException("Expected intmax in filestatus to be a number");
        }
    }

    public Point getHighestUnassignedPoint() throws Exception {
        JsonElement resp = doHttpGet("gethighestuntraced");
        if (resp.isJsonArray()) {
            JsonArray pointData = resp.getAsJsonArray();
            return new Point(pointData);
        } else if (resp.isJsonNull()) {
            return null;
        } else {
            throw new Exception("Expected gethighestuntraced to return a point or null");
        }
    }

    public int getNextTraceId() throws Exception {
        JsonElement resp = doHttpGet("getnextids");
        if (resp.isJsonObject()) {
            return resp.getAsJsonObject().get("nextTrace").getAsInt();
        } else {
            throw new Exception("Expected getnextids to return a JSON object");
        }
    }

    public void writePoint(int traceId, Point point) throws IOException {
        String requestText = "[{\"type\": \"set-trace\", \"trace\": " + traceId
                + ",\"points\": [" + point.id + "]}]";
        HttpEntity requestEntity = new StringEntity(requestText);

        URI uri = serverRoot.resolve(API_PATH).resolve("updatesegmentation");
        HttpPost request = new HttpPost(uri);
        request.setEntity(requestEntity);
        request.setHeader("Content-Type", "application/json");
        httpClient.execute(request).close();
    }

    @Override
    public double getMzResolution(double nearMz) throws Exception {
        throw new UnsupportedOperationException("Not implemented");
    }
}
