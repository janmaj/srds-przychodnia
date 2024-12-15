package cassdemo.util;

import java.util.UUID;

public class Util {
    public static int generateUUID(){
        UUID uuid = UUID.randomUUID();
        return Math.abs(uuid.hashCode());
    }
}
