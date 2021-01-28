package functions;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Logger;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.ByteArrayContent;
import com.google.api.client.http.GenericUrl;
import com.google.api.client.http.HttpContent;
import com.google.api.client.http.HttpRequest;
import com.google.api.client.http.HttpRequestFactory;
import com.google.api.client.http.HttpRequestInitializer;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.services.chat.v1.model.Card;
import com.google.api.services.chat.v1.model.CardHeader;
import com.google.api.services.chat.v1.model.KeyValue;
import com.google.api.services.chat.v1.model.Message;
import com.google.api.services.chat.v1.model.Section;
import com.google.api.services.chat.v1.model.WidgetMarkup;
import com.google.api.services.cloudbuild.v1.CloudBuild;
import com.google.api.services.cloudbuild.v1.model.BuildTrigger;
import com.google.auth.http.HttpCredentialsAdapter;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.functions.BackgroundFunction;
import com.google.cloud.functions.Context;

import functions.PubSubChatFunction.PubSubMessage;

public class PubSubChatFunction implements BackgroundFunction<PubSubMessage> {

	private static final Logger logger = Logger.getLogger(PubSubChatFunction.class.getName());

	private static final String URI = System.getenv("CHAT_WEBHOOK");
	
	private static final HttpTransport httpTransport = getHttpTransport();
	private static final JacksonFactory jacksonFactory = new JacksonFactory();
	private static final HttpRequestFactory requestFactory = httpTransport.createRequestFactory();
	private static final CloudBuild cloudBuildService = getCloudBuildService();
	private static final ObjectMapper objectMapper = new ObjectMapper();
	
	public void accept(PubSubMessage message, Context context) {

		BuildInfo buildInfo = getBuildInfoFromPubSubMessage(message);
    
		if(buildInfo.status.equals("QUEUED") || buildInfo.triggerId == null) {
			return ;
		}
		
	    Optional<BuildTrigger> triggerData = getTriggerData(buildInfo.projectId, buildInfo.triggerId);
	    
		Message botMessage = buildBotMessage(buildInfo, triggerData);
		
		sendMessageToBot(botMessage);
	}

	private static CloudBuild getCloudBuildService( ) {
	
		try {
			
			GoogleCredentials credential = GoogleCredentials.getApplicationDefault();
		
			HttpRequestInitializer requestInitializer = new HttpCredentialsAdapter(credential);        
		    
			return new CloudBuild.Builder(httpTransport, jacksonFactory, requestInitializer).build();
		 
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}
	
	private static HttpTransport getHttpTransport() {
		try {
			return GoogleNetHttpTransport.newTrustedTransport();
		} catch (GeneralSecurityException | IOException e) {
			throw new RuntimeException(e);
		}
	}
	
	private BuildInfo getBuildInfoFromPubSubMessage(PubSubMessage message) {
	
		try {
			BuildInfo result = new BuildInfo();
			
			Map<String, String> valueMap = objectMapper.readValue(new String(Base64.getDecoder().decode(message.data)), Map.class);
			
			result.buildId = valueMap.get("buildId");
			result.endTime = valueMap.get("finishTime");
			result.startTime = valueMap.get("startTime");
			result.projectId = valueMap.get("projectId");
			result.status = valueMap.get("status");
			result.triggerId = valueMap.get("buildTriggerId");
			
			return result;
			
		} catch (JsonProcessingException e) {
			throw new RuntimeException(e);
		}
	}
	
	private Optional<BuildTrigger> getTriggerData(String projectId, String triggerId) {
		
		try {
		
			if(triggerId != null) {
					return Optional.of(cloudBuildService.projects().triggers().get(projectId, triggerId).execute());
			} else {
				return Optional.empty();
			}
		
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}
	
	private Message buildBotMessage(BuildInfo buildInfo,  Optional<BuildTrigger> trigger ) {
		
		Message message = new Message();
	    
	    Card card = new Card();
	    CardHeader header = new CardHeader();
	    header.setTitle("Debo - The Deployer Bot");
	    header.setImageUrl("https://goo.gl/aeDtrS");
	    card.setHeader(header);
	    
	    Section section = new Section();
	    
	    section.setWidgets(Arrays.asList(
	    		new WidgetMarkup().setKeyValue(new KeyValue().setTopLabel("Project id").setContent(buildInfo.projectId)),
	    		new WidgetMarkup().setKeyValue(new KeyValue().setTopLabel("Trigger Name").setContent(trigger.isPresent() ? trigger.get().getName() : " - ")),
	    		new WidgetMarkup().setKeyValue(new KeyValue().setTopLabel("Branch Name").setContent(trigger.isPresent() ? trigger.get().getTriggerTemplate().getBranchName() : " - ")),
	    		new WidgetMarkup().setKeyValue(new KeyValue().setTopLabel("Status").setContent(buildInfo.status)),
	    		new WidgetMarkup().setKeyValue(new KeyValue().setTopLabel("Start Time").setContent(buildInfo.startTime != null ? buildInfo.startTime : " - ")),
	    		new WidgetMarkup().setKeyValue(new KeyValue().setTopLabel("Finish Time").setContent(buildInfo.endTime != null ? buildInfo.endTime : " - "))
	    		));
	    
	    card.setSections(Collections.singletonList(section));
	    
	    message.setCards(Collections.singletonList(card));
	    
	    return message;
	}
	
	private void sendMessageToBot(Message botMessage) {
		
		try {
			
			GenericUrl url = new GenericUrl(URI);
			
			HttpContent content = new ByteArrayContent("application/json", objectMapper.writeValueAsBytes(botMessage));
		    HttpRequest request = requestFactory.buildPostRequest(url, content);
		    request.execute();
		    
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}
	
	public static class BuildInfo {
		String projectId;
		String status;
		String buildId;
		String startTime;
		String endTime;
		String triggerId;
	}
	
	public static class PubSubMessage {
		String data;
		Map<String, String> attributes;
		String messageId;
		String publishTime;
	}
	
}
