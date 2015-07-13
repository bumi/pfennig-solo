package pfennig;

import java.util.Map;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.kevinsawicki.http.HttpRequest;
import com.google.common.io.BaseEncoding;

public class Utils {
    static Logger logger = LoggerFactory.getLogger(Utils.class.getName());
    static String HMAC_KEY;

    public static String calculateHMAC(String data) {
        String result;
        try {
            SecretKeySpec signingKey = new SecretKeySpec(HMAC_KEY.getBytes("UTF-8"), "HmacSHA256");
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(signingKey);
            byte[] rawHmac = mac.doFinal(data.getBytes("UTF-8"));
            result = BaseEncoding.base16().lowerCase().encode(rawHmac);
        } catch (Exception e) {
            logger.error("faild to generate hmac: " + e.getMessage());
            result = "";
        }

        return result;
    }

    public static boolean sendNotification(String url, String body) {
        HttpRequest request = HttpRequest.post(url);

        request.header("Content-Type", "application/json");
        if (Utils.HMAC_KEY != null && !Utils.HMAC_KEY.isEmpty()) {
            request.header("X-PFENNIG-VERIFICATION", Utils.calculateHMAC(body));
        }
        try {
            request.send(body);

            if (request.ok()) {
                logger.info("notification successful url: " + url);
                logger.debug("notification response: " + request.body());
                return true;
            } else {
                logger.error("notification failed url: " + url + " status: " + request.code());
                logger.debug("notification response body: " + request.body());
                return false;
            }
        } catch (Exception e) {
            logger.error("notification failed url: " + url + " - " + e.getMessage());
            return false;
        }
    }

    public static int chainHeightFromBlockChainInfo() {
        int chainHeight;
        try {
            String blockChainInfoResponse = HttpRequest.get("https://blockchain.info/latestblock").body();
            logger.info("blockchain.info latest block: " + blockChainInfoResponse);
            Map blockInfo = (Map) new JSONParser().parse(blockChainInfoResponse);
            Object height = blockInfo.get("height");
            chainHeight = ((Long) height).intValue();
        } catch (ParseException e) {
            e.printStackTrace();
            logger.error("could not get chain height from blockchain.info: " + e.getMessage());
            chainHeight = 0;
        }
        return chainHeight;
    }
}
