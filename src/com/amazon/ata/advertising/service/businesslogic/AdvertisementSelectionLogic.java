package com.amazon.ata.advertising.service.businesslogic;

import com.amazon.ata.advertising.service.dao.ReadableDao;
import com.amazon.ata.advertising.service.model.*;
import com.amazon.ata.advertising.service.targeting.TargetingEvaluator;
import com.amazon.ata.advertising.service.targeting.TargetingGroup;

import com.amazon.ata.advertising.service.targeting.predicate.TargetingPredicate;
import com.amazon.ata.advertising.service.targeting.predicate.TargetingPredicateResult;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;
import javax.inject.Inject;

import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;

/**
 * This class is responsible for picking the advertisement to be rendered.
 */
public class AdvertisementSelectionLogic {

    private static final Logger LOG = LogManager.getLogger(AdvertisementSelectionLogic.class);

    private final ReadableDao<String, List<AdvertisementContent>> contentDao;
    private final ReadableDao<String, List<TargetingGroup>> targetingGroupDao;
    private Random random = new Random();

    /**
     * Constructor for AdvertisementSelectionLogic.
     *
     * @param contentDao        Source of advertising content.
     * @param targetingGroupDao Source of targeting groups for each advertising content.
     */
    @Inject
    public AdvertisementSelectionLogic(ReadableDao<String, List<AdvertisementContent>> contentDao,
                                       ReadableDao<String, List<TargetingGroup>> targetingGroupDao) {
        this.contentDao = contentDao;
        this.targetingGroupDao = targetingGroupDao;
    }

    /**
     * Setter for Random class.
     *
     * @param random generates random number used to select advertisements.
     */
    public void setRandom(Random random) {
        this.random = random;
    }

    /**
     * Gets all of the content and metadata for the marketplace and determines which content can be shown.  Returns the
     * eligible content with the highest click through rate.  If no advertisement is available or eligible, returns an
     * EmptyGeneratedAdvertisement.
     *
     * @param customerId    - the customer to generate a custom advertisement for
     * @param marketplaceId - the id of the marketplace the advertisement will be rendered on
     * @return an advertisement customized for the customer id provided, or an empty advertisement if one could
     * not be generated.
     */
    public GeneratedAdvertisement selectAdvertisement(String customerId, String marketplaceId)  {
        GeneratedAdvertisement generatedAdvertisement = new EmptyGeneratedAdvertisement();

        SortedMap<TargetingGroup, AdvertisementContent> sortedMap =
                new TreeMap<>(Comparator.comparingDouble(TargetingGroup::getClickThroughRate).reversed());
        if (StringUtils.isEmpty(marketplaceId)) {
            LOG.warn("MarketplaceId cannot be null or empty. Returning empty ad.");
        } else {
            RequestContext requestContext = new RequestContext(customerId, marketplaceId);
            TargetingEvaluator targetingEvaluator = new TargetingEvaluator(requestContext);
            for (AdvertisementContent content : contentDao.get(marketplaceId)) {
                 targetingGroupDao.get(content.getContentId())
                         .stream()
                         .sorted(sortedMap.comparator())
                         .filter(targetingGroup -> targetingEvaluator.evaluate(targetingGroup).isTrue())
                         .findFirst()
                         .ifPresent(targetingGroup -> sortedMap.put(targetingGroup, content));

            }
            if (!sortedMap.isEmpty()) {
                final AdvertisementContent preparedContent = sortedMap.get(sortedMap.firstKey());
                return new GeneratedAdvertisement(preparedContent);
            }



        }
        return generatedAdvertisement;


    }
}
//           generatedAdvertisement = new GeneratedAdvertisement(contentDao.get(marketplaceId).stream()
//                    .map(advertisementContent -> targetingGroupDao.get(advertisementContent.getContentId())
//                            .stream()
//                            .sorted(sortedMap.comparator())
////                            .sorted(Comparator.comparingDouble(TargetingGroup::getClickThroughRate))
//                            .map(targetingEvaluator::evaluate)
//                            .anyMatch(TargetingPredicateResult::isTrue) ? advertisementContent : null)
//                    .filter(Objects::nonNull)
//                    .findFirst()
//                   .get());