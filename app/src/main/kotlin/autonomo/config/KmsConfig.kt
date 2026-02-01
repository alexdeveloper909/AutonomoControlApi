package autonomo.config

import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.kms.KmsClient

object KmsConfig {
    fun client(): KmsClient {
        return KmsClient.builder()
            .region(Region.of(AppConfig.region))
            .build()
    }
}

