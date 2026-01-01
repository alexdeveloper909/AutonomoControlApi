package autonomo.config

import java.net.URI
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.dynamodb.DynamoDbClient

object DynamoConfig {
    fun client(): DynamoDbClient {
        val builder = DynamoDbClient.builder()
            .region(Region.of(AppConfig.region))

        if (AppConfig.isLocal) {
            builder
                .endpointOverride(URI.create(AppConfig.dynamoEndpoint))
                .credentialsProvider(
                    StaticCredentialsProvider.create(
                        AwsBasicCredentials.create("dummy", "dummy")
                    )
                )
        }

        return builder.build()
    }
}
