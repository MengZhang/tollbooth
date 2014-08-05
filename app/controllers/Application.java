package controllers;

import java.util.UUID;

import com.fasterxml.jackson.databind.node.ObjectNode;

import com.google.common.base.Charsets;
import com.google.common.hash.HashCode;
import com.google.common.hash.HashFunction;
import com.google.common.hash.Hashing;

import play.cache.Cache;
import play.data.DynamicForm;
import play.data.Form;
import play.libs.Json;
import play.libs.ws.*;
import play.libs.F.Function;
import play.mvc.BodyParser;
import play.mvc.Controller;
import play.mvc.Http.Cookie;
import play.mvc.Result;
//import play.Logger;
import plugins.RestedCrowdPlugin;

import util.TollboothConfig;

import views.html.login;
import views.html.profile;



public class Application extends Controller {
    // Parameters required to send a user along
    // * for <= Referring URL
    // * refId <= Requesting Host Secret (so we aren't abused)
    public static HashFunction hf = Hashing.sha1();
    
    public static Result index() {
        Form<TollboothLogin> loginForm = new Form<>(TollboothLogin.class);
        String forUrl = DynamicForm.form().bindFromRequest().get("for");
        String externService = DynamicForm.form().bindFromRequest().get("s");
        Cookie ssoToken = request().cookie(RestedCrowdPlugin.getCookieSettings().getName());
        if (ssoToken != null) {
            // We need to validate this cookie
            if (RestedCrowdPlugin.validCrowdSession(ssoToken.value())) {
            	if (session().get("uid") == null) {
            		session().put("uid", RestedCrowdPlugin.getCrowdEmail(ssoToken.value()));
            	}
                if( externService != null && ! externService.equals("")) {
                    String key = putExternCache(externService, ssoToken.value());
                    if (key != null) {
                        return redirect(TollboothConfig.getServiceUrl(externService)+"?nextval="+key);
                    } else {
                        return ok(profile.render());
                    }
                }
                if (forUrl != null) {
                    return redirect(forUrl);
                } else { 
                    return ok(profile.render());
                }
            } else {
            	session().clear();
                response().discardCookie(RestedCrowdPlugin.getCookieSettings().getName(), "/", ".agmip.org");
                // Add an indicator for the user to know that their session has
                // expired here.
                return ok(login.render(loginForm, forUrl, externService));
            }
        } else {
            return ok(login.render(loginForm, forUrl, externService));
        }
    }

    public static Result authenticate() {
        Form<TollboothLogin> loginForm = new Form<>(TollboothLogin.class).bindFromRequest();
        String forUrl = DynamicForm.form().bindFromRequest().get("for");
        String externService = DynamicForm.form().bindFromRequest().get("s");
        if (loginForm.hasErrors()) {
            return badRequest(login.render(loginForm, forUrl, externService));
        } else {
            if (externService != null && ! externService.equals("")) {
                Cookie ssoToken = null;
                for (Cookie cookie : response().cookies()) {
                    if (cookie.name().equals(RestedCrowdPlugin.getCookieSettings().getName())) {
                        ssoToken = cookie;
                    }
                }
                if (ssoToken != null) {
                    String key = putExternCache(externService, ssoToken.value());
                    if (key != null) {
                        return redirect(TollboothConfig.getServiceUrl(externService)+"?nextval="+key);
                    } else {
                        return ok(profile.render());
                    }
                } else {
                    return redirect("/");
                }
            }
            return redirect(forUrl);
        }
    }

    public static Result logout() {
        Cookie ssoToken = request().cookie(RestedCrowdPlugin.getCookieSettings().getName());
        String forUrl = DynamicForm.form().bindFromRequest().get("for");
        if (ssoToken != null) {
            RestedCrowdPlugin.crowdRequest("session/"+ssoToken.value()).delete().get(30000);
            session().clear();
            response().discardCookie(RestedCrowdPlugin.getCookieSettings().getName(), "/", ".agmip.org");
        }
        if (forUrl != null) {
            return redirect(forUrl);
        } else {
            return redirect("/");
        }
    }

    // REST Endpoints (JSON)

    /**
     * Lookup the email address associated with a given SSO token
     * @param token
     * @return email address of current user
     */
    @BodyParser.Of(BodyParser.Json.class)
    public static Result lookupEmail(String token) {
        ObjectNode result = Json.newObject();
        if (token == null || ! RestedCrowdPlugin.validCrowdSession(token)) {
            return forbidden(result);
        }
        String email = RestedCrowdPlugin.getCrowdEmail(token);
        if (email == null) {
            email = "";
        }
        result.put("email", email);
        return ok(result);
    }

    /**
     * Validate the current session associated with a given SSO token
     * 
     * @param token
     * @return HTTP Status 200 if valid or HTTP Status 403 if invalid.
     */
    @BodyParser.Of(BodyParser.Json.class)
    public static Result validateToken(String token) {
    	if (RestedCrowdPlugin.validCrowdSession(token)) {
    		return ok();
    	} else {
    		// TODO: Return the reason this is invalid.
    		//ObjectNode result = Json.newObject();
    		return forbidden();
    	}
    }

    public static Result pullExternCache() {
        String key      = DynamicForm.form().bindFromRequest().get("vector");
        String ssoToken = null;
        if(key != null) {
            ssoToken = (String) Cache.get(key);
        }
        if(ssoToken != null) {
            return ok(ssoToken);
        } else {
            return forbidden();
        }
    }


    /**
     * Validate a POSTed SSO token
     *
     * @return HTTP Status 200 if valid or HTTP status 403 if invalid
     */
    public static Result postValidateToken() {
        String token = DynamicForm.form().bindFromRequest().get("stv");
        if(RestedCrowdPlugin.validCrowdSession(token)) {
            return ok();
        } else {
            return forbidden();
        }
    }

    /**
     * Basic class for verifying username and password. Simple for
     * modeling purposes. Based on Zentasks from Play Framework
     * tutorial.
     * 
     * @author Christopher Villalobos
     *
     */
    public static class TollboothLogin {
        public String username;
        public String password;
        public String token;
        
        public String validate() {
            String authString = "{\"username\":\""+username+"\", "+
            		"\"password\":\""+password+"\", "+
                    "\"validation-factors\":"+RestedCrowdPlugin.VALIDATION_FACTORS+
                    "}";
            return RestedCrowdPlugin.crowdRequest("session")
		.post(authString)
		.map(
		     new Function<WSResponse, String>() {
			 public String apply(WSResponse res) throws Throwable {
			     int statusCode = res.getStatus();
			     if(statusCode == 201) {
				 // Set the token here, since no other place has knowledge of the
				 // token
				 String cookieSettings = RestedCrowdPlugin.getCookieSettings().getName();
				 response().setCookie(RestedCrowdPlugin.getCookieSettings().getName(),
						      res.asJson().findPath("token").asText(),
						      null, "/", ".agmip.org");
				 return null;
			     } else if(statusCode == 400 || statusCode == 403) {
				 return "Invalid username or password.";
			     } else {
				 return "We've encountered a server error.";
			     }
			 }
		     }).get(30000);
	}
    }

    // Methods

    public static String putExternCache(String svc, String ssoToken) {
        String externUrl = TollboothConfig.getServiceUrl(svc);
        String sign = TollboothConfig.getServiceKey(svc);

        if(externUrl == null || sign == null) {
            return null;
        }
        String uniq = UUID.randomUUID().toString();
        String cacheToken = hf.newHasher()
            .putString(uniq, Charsets.UTF_8)
            .putString(":", Charsets.UTF_8)
            .putString(sign, Charsets.UTF_8)
            .hash().toString();
        Cache.set(cacheToken, ssoToken, 2*60); // Cache time in minutes * 60
        return uniq;
    }
}
