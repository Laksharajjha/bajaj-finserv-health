package com.example.bajaj_finserv_health;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import javax.annotation.PostConstruct;
import java.time.Duration;
import java.util.*;

@SpringBootApplication
public class BajajFinservHealthApplication {

	@Autowired
	private WebClient webClient;

	@Autowired
	private ObjectMapper objectMapper;

	public static void main(String[] args) {
		SpringApplication.run(BajajFinservHealthApplication.class, args);
	}

	@PostConstruct
	public void init() {
		ObjectNode requestBody = objectMapper.createObjectNode();
		requestBody.put("name", "Laksharaj Jha");
		requestBody.put("regNo", "RA2211003050003");
		requestBody.put("email", "lj0206@srmist.edu.in");

		webClient.post()
				.uri("/hiring/generateWebhook")
				.contentType(MediaType.APPLICATION_JSON)
				.bodyValue(requestBody)
				.retrieve()
				.bodyToMono(JsonNode.class)
				.flatMap(response -> {
					String webhook = response.get("webhook").asText();
					String accessToken = response.get("accessToken").asText();
					JsonNode usersData = response.get("data").get("users");
					int findId = usersData.get("findId").asInt();
					int n = usersData.get("n").asInt();
					JsonNode users = usersData.get("users");

					List<Integer> result = findNthLevelFollowers(users, findId, n);

					ObjectNode outcome = objectMapper.createObjectNode();
					outcome.put("regNo", "RA2211003050003");
					ArrayNode outcomeArray = outcome.putArray("outcome");
					result.forEach(outcomeArray::add);

					return webClient.post()
							.uri(webhook)
							.header(HttpHeaders.AUTHORIZATION, accessToken)
							.contentType(MediaType.APPLICATION_JSON)
							.bodyValue(outcome)
							.retrieve()
							.bodyToMono(Void.class)
							.retryWhen(Retry.fixedDelay(4, Duration.ofSeconds(1)));
				})
				.onErrorResume(e -> {
					System.err.println("Error during webhook interaction: " + e.getMessage());
					return Mono.empty();
				})
				.subscribe();
	}

	private List<Integer> findNthLevelFollowers(JsonNode users, int findId, int n) {
		Map<Integer, List<Integer>> graph = new HashMap<>();
		for (JsonNode user : users) {
			int userId = user.get("id").asInt();
			JsonNode follows = user.get("follows");
			List<Integer> followList = new ArrayList<>();
			for (JsonNode followId : follows) {
				followList.add(followId.asInt());
			}
			graph.put(userId, followList);
		}

		Queue<Integer> queue = new LinkedList<>();
		Map<Integer, Integer> distance = new HashMap<>();
		Set<Integer> visited = new HashSet<>();

		queue.add(findId);
		distance.put(findId, 0);
		visited.add(findId);

		while (!queue.isEmpty()) {
			int current = queue.poll();
			int currentDistance = distance.get(current);

			List<Integer> neighbors = graph.getOrDefault(current, Collections.emptyList());
			for (int neighbor : neighbors) {
				if (!visited.contains(neighbor)) {
					visited.add(neighbor);
					distance.put(neighbor, currentDistance + 1);
					queue.add(neighbor);
				}
			}
		}

		List<Integer> result = new ArrayList<>();
		for (Map.Entry<Integer, Integer> entry : distance.entrySet()) {
			if (entry.getValue() == n) {
				result.add(entry.getKey());
			}
		}

		Collections.sort(result);
		return result;
	}
}