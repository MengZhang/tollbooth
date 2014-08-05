package util;

import play.Play;

public enum TollboothConfig {
	INSTANCE;
	
	public static final String tollboothUrl;   
	
	static {
		tollboothUrl    = rewriteUrl(Play.application().configuration().getString("tollbooth.baseurl", "http://localhost:9110/"));
	}
	
	private static String rewriteUrl(String url) {
		if (! url.endsWith("/")) {
			return url+"/";
		} else {
			return url;
		}
	}

    public static String getServiceUrl(String svc) {
        if (svc == null || svc.equals("")) return null;
        return Play.application().configuration().getString("extern."+svc.toLowerCase()+".url");
    }

    public static String getServiceKey(String svc) {
        if (svc == null || svc.equals("")) return null;
        return Play.application().configuration().getString("extern."+svc.toLowerCase()+".key");
    }
}
