package au.org.ala.ws.security.authenticator;

import au.org.ala.userdetails.UserDetailsClient;
import au.org.ala.web.UserDetails;
import au.org.ala.ws.security.ApiKeyClient;
import au.org.ala.ws.security.CheckApiKeyResult;
import au.org.ala.ws.security.profile.AlaApiUserProfile;
import org.pac4j.core.context.WebContext;
import org.pac4j.core.context.session.SessionStore;
import org.pac4j.core.credentials.Credentials;
import org.pac4j.core.credentials.TokenCredentials;
import org.pac4j.core.credentials.authenticator.Authenticator;
import org.pac4j.core.exception.CredentialsException;
import org.pac4j.core.util.CommonHelper;
import org.pac4j.core.util.InitializableObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import retrofit2.Call;
import retrofit2.Response;

import java.io.IOException;
import java.util.Map;

public class AlaApiKeyAuthenticator extends InitializableObject implements Authenticator {

    public static final Logger log = LoggerFactory.getLogger(AlaApiKeyAuthenticator.class);

    @Override
    protected void internalInit(boolean forceReinit) {
        CommonHelper.assertNotNull("apiKeyClient", apiKeyClient);
        CommonHelper.assertNotNull("userDetailsClient", userDetailsClient);
    }

    @Override
    public void validate(Credentials credentials, WebContext context, SessionStore sessionStore) {

        init();

        TokenCredentials alaApiKeyCredentials = (TokenCredentials) credentials;

        AlaApiUserProfile alaApiUserProfile = null;
        try {
            alaApiUserProfile = fetchUserProfile(alaApiKeyCredentials.getToken());
        } catch (IOException e) {
            log.warn("Couldn't fetch user profile", e);
            throw new CredentialsException("Coudln't fetch user profile");
        }

        if (alaApiUserProfile.isActivated() && !alaApiUserProfile.isLocked()) {

            alaApiKeyCredentials.setUserProfile(alaApiUserProfile);
        }

    }

    public AlaApiUserProfile fetchUserProfile(final String apiKey) throws IOException {

        AlaApiUserProfile alaApiUserProfile = new AlaApiUserProfile();

        Call<CheckApiKeyResult> checkApiKeyCall = apiKeyClient.checkApiKey(apiKey);

        final Response<CheckApiKeyResult> checkApiKeyResponse = checkApiKeyCall.execute();

        if (!checkApiKeyResponse.isSuccessful()) {
            throw new CredentialsException("apikey check failed : " + checkApiKeyResponse.message());
        }


        CheckApiKeyResult apiKeyCheck = checkApiKeyResponse.body();

        if (apiKeyCheck.getValid()) {

            String userId = apiKeyCheck.getUserId();

            alaApiUserProfile.setUserId(userId);
            alaApiUserProfile.setEmail(apiKeyCheck.getEmail());

            Call<UserDetails> userDetailsCall = userDetailsClient.getUserDetails(userId, true);

            Response<UserDetails> response = userDetailsCall.execute();

            if (response.isSuccessful()) {

                UserDetails userDetails = response.body();

                alaApiUserProfile.setGivenName(userDetails.getFirstName());
                alaApiUserProfile.setFamilyName(userDetails.getLastName());
                alaApiUserProfile.setActivated(userDetails.getActivated());
                final Boolean locked = userDetails.getLocked();
                alaApiUserProfile.setLocked(locked != null ? locked : true);
                alaApiUserProfile.addRoles(userDetails.getRoles());
                // TODO this isn't quite right
                alaApiUserProfile.setAttributes((Map)userDetails.getProps());
            }


            return alaApiUserProfile;
        }


        throw new CredentialsException("invalid apiKey: '" + apiKey + "'");
    }

    public ApiKeyClient getApiKeyClient() {
        return apiKeyClient;
    }

    public void setApiKeyClient(ApiKeyClient apiKeyClient) {
        this.apiKeyClient = apiKeyClient;
    }

    public UserDetailsClient getUserDetailsClient() {
        return userDetailsClient;
    }

    public void setUserDetailsClient(UserDetailsClient userDetailsClient) {
        this.userDetailsClient = userDetailsClient;
    }

    private ApiKeyClient apiKeyClient;
    private UserDetailsClient userDetailsClient;
}
