package com.spimex.tz;


import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

@WebServlet(
        name = "ActiveServlet",
        urlPatterns = {"/active"}
)
public class ActiveServlet extends HttpServlet {
    public static final String DEFAULT_URL = "https://api.spimex.com/otc/lookup-tables/1";

    public static final int[] KOEF = {2,4,10,3,5,9,4,6,8,0};

    private ObjectMapper objectMapper;
    private HttpClient client;

    @Override
    public void init() throws ServletException {
        super.init();
        objectMapper = new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        client = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(20))
                .build();
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        String regString = req.getParameter("region");
        int region = regString == null || regString.isBlank() ? 99 : Integer.parseInt(regString);

        String url = req.getParameter("url");
        if (url == null || url.isBlank()) url = DEFAULT_URL;

        ActiveResult ar;
        try {
            ar = getActiveResult(region, new URL(url));
        } catch (URISyntaxException | InterruptedException e) {
            throw new ServletException(e);
        }
        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");
        resp.getWriter().write(objectMapper.writeValueAsString(ar));
    }

    private ActiveResult getActiveResult(int region, URL url) throws IOException, URISyntaxException, InterruptedException, ServletException {
        if (region > 99 || region < 1)
            throw new ServletException("не правильно задан регион. Возможные значения 0 < регион < 100");
        HttpRequest request = HttpRequest.newBuilder()
                .GET()
                .uri(url.toURI())
                .build();
        HttpResponse<InputStream> resp = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
        if (resp.statusCode() != 200) throw new ServletException("wrong response code: " + resp.statusCode());
        ResultOrganizations ros = objectMapper.readValue(resp.body(), ResultOrganizations.class);

        Set<String> inns = Arrays.stream(ros.records()).parallel()
                .filter(o -> o.blockDate() == null || o.blockDate().isBlank())
                .filter(o -> "Резидент РФ".equals(o.residence()))
                .map(Organization::inn)
                .filter(inn -> inn != null && inn.length() == 10)//ИНН ЮЛ 10 символов
                .filter(ActiveServlet::isNumber)
                .filter(ActiveServlet::isINN)
                .collect(Collectors.toSet());

        //считаем межрегиональные
        long region99 = inns.stream().filter(inn -> inn.startsWith("99")).count();
        long countRegion = 0;
        int all = inns.size();
        String message;
        if (region == 99)
            message = String.format("Юридических лиц зарегистрированных в межрегиональных инспекциях (код региона 99): %d",
                    region99);
        else {
            countRegion = inns.stream().filter(inn -> inn.startsWith(String.format("%02d", region))).count();
            message = String.format("""
                    В регионе %s зарегистрировано юридических лиц: %d\r
                    Юридических лиц зарегистрированных в межрегиональных инспекциях: %d\r
                    Итого: %d""", region, countRegion, region99, countRegion + region99);
        }
        return new ActiveResult(countRegion, region99, all, message);
    }

    private static boolean isNumber(String s) {
        try {
            Long.parseLong(s);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private static boolean isINN(String s) {
        int sum = 0;
        for (int i = 0; i < 10; i++)
            sum += Character.getNumericValue(s.charAt(i)) * KOEF[i];

        int mod = sum % 11;
        if (mod == 10) mod = 0;
        int lastDigit = Character.getNumericValue(s.charAt(9));
        return lastDigit == mod;
    }

    public static void main(String[] args) throws Exception {
        int region = 55;
        if (args.length > 0)
            region = Integer.parseInt(args[0]);
        String url = DEFAULT_URL;
        if (args.length > 1)
            url = args[1];
        ActiveServlet as = new ActiveServlet();
        as.init();
        System.out.println(as.getActiveResult(region, new URL(url)));
    }
}
