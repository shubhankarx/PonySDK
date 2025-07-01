package com.ponysdk.sample.client;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

public class FastApiTestClient {

    public static void main(String[] args) throws IOException {
        // 1. Define the URL of your FastAPI server endpoint.
        //    Make sure your FastAPI server is running on http://127.0.0.1:8000/
        URL url = new URL("http://127.0.0.1:8000/"); 
        
        // 2. Open a connection to the URL.
        //    This creates an HttpURLConnection object, which is used to send and receive data.
        HttpURLConnection con = (HttpURLConnection) url.openConnection();
        
        // 3. Set the request method to POST.
        //    This indicates that you are sending data to the server.
        con.setRequestMethod("POST");
        
        // 4. Set the Content-Type header to "application/json".
        //    This tells the server that the body of your request is a JSON string.
        con.setRequestProperty("Content-Type", "application/json");
        
        // 5. Set the Accept header to "application/json".
        //    This tells the server that you prefer to receive a JSON response.
        con.setRequestProperty("Accept", "application/json");
        
        // 6. Enable output for the connection.
        //    This is necessary because you are going to write data (the JSON string) to the connection's output stream.
        con.setDoOutput(true);
        
        // 7. Manually construct the JSON string payload.
        //    This is the data you want to send to your FastAPI endpoint.
        //    It directly matches the 'User' BaseModel structure in your Python example.
        String jsonInputString = "{\"user\": \"foo\"}";

        // 8. Write the JSON string to the connection's output stream.
        //    The try-with-resources statement ensures the OutputStream is properly closed.
        try (OutputStream os = con.getOutputStream()) {
            // 8a. Convert the JSON string into bytes using UTF-8 encoding.
            byte[] input = jsonInputString.getBytes(StandardCharsets.UTF_8);
            // 8b. Write the byte array to the output stream.
            os.write(input, 0, input.length);
        }

        // 9. Read the response from the server.
        //    The try-with-resources statement ensures the BufferedReader is properly closed.
        try (BufferedReader br = new BufferedReader(new InputStreamReader(con.getInputStream(), StandardCharsets.UTF_8))) {
            StringBuilder response = new StringBuilder();
            String responseLine = null;
            // 9a. Read each line of the response until there are no more lines.
            while ((responseLine = br.readLine()) != null) {
                // 9b. Append the trimmed response line to the StringBuilder.
                response.append(responseLine.trim());
            }
            // 10. Print the HTTP response code and the received response body.
            //     A 200 OK code indicates success, followed by the JSON response from FastAPI.
            System.out.println(con.getResponseCode() + " " + response);
        } finally {
            // 11. Disconnect the HttpURLConnection.
            //     This releases resources and closes the connection.
            con.disconnect();
        }
    }
} 