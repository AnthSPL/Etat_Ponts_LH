package fr.anthspl.etatpontlehavre.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class MeteoFranceVigilanceRoot(
    @SerialName("product")
    val product: MeteoFranceProduct? = null
)

@Serializable
data class MeteoFranceProduct(
    @SerialName("warning_type")
    val warningType: String? = null,
    @SerialName("domain_id")
    val domainId: String? = null,
    @SerialName("periods")
    val periods: List<VigilancePeriod>? = null
)

@Serializable
data class VigilancePeriod(
    @SerialName("echeance")
    val echeance: String? = null,
    @SerialName("begin_validity_time")
    val beginValidityTime: String? = null,
    @SerialName("end_validity_time")
    val endValidityTime: String? = null,
    @SerialName("timelaps")
    val timelaps: VigilanceTimelaps? = null
)

@Serializable
data class VigilanceTimelaps(
    @SerialName("domain_ids")
    val domainIds: List<VigilanceDomainItem>? = null
)

@Serializable
data class VigilanceDomainItem(
    @SerialName("domain_id")
    val domainId: String? = null,
    @SerialName("max_color_id")
    val maxColorId: Int? = null,
    @SerialName("phenomenon_items")
    val phenomenonItems: List<VigilancePhenomenonItem>? = null
)

@Serializable
data class VigilancePhenomenonItem(
    @SerialName("phenomenon_id")
    val phenomenonId: String? = null,
    @SerialName("phenomenon_max_color_id")
    val phenomenonMaxColorId: Int? = null,
    @SerialName("timelaps_items")
    val timelapsItems: List<VigilanceTimelapsItem>? = null
)

@Serializable
data class VigilanceTimelapsItem(
    @SerialName("begin_time")
    val beginTime: String? = null,
    @SerialName("end_time")
    val endTime: String? = null,
    @SerialName("color_id")
    val colorId: Int? = null
)