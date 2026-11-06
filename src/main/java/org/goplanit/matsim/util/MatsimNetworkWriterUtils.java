package org.goplanit.matsim.util;

import org.goplanit.converter.idmapping.NetworkIdMapper;
import org.goplanit.utils.graph.directed.EdgeSegment;
import org.goplanit.utils.id.IdMapperType;
import org.goplanit.utils.network.layer.macroscopic.MacroscopicLinkSegment;

import java.util.Map;
import java.util.concurrent.atomic.LongAdder;
import java.util.logging.Logger;

public class MatsimNetworkWriterUtils {

  private static final Logger LOGGER = Logger.getLogger(MatsimNetworkWriterUtils.class.getCanonicalName());

  /** Make sure that if external id is used that it is unique even if it is not originally, otherwise use the
   * regular mapped id provided
   *
   * @param linkSegment to check for
   * @param idMapperType chosen idMapper type
   * @param networkSegmentIdMapper to verify and use in default setup
   * @param alreadyUsedExternalMatsimIds that are used already
   * @return unique externalId (if not external id then copy of original is returned
   */
  public static String produceMappedMatsimLinkId(
      MacroscopicLinkSegment linkSegment,
      final IdMapperType idMapperType,
      final NetworkIdMapper networkSegmentIdMapper,
      final Map<String, LongAdder> alreadyUsedExternalMatsimIds) {

    String matsimMappedId = networkSegmentIdMapper.getMacroscopicLinkSegmentIdMapper().apply(linkSegment);
    if(idMapperType == IdMapperType.EXTERNAL_ID) {
      if(alreadyUsedExternalMatsimIds.containsKey(matsimMappedId)) {
        LongAdder duplicateCount = alreadyUsedExternalMatsimIds.get(matsimMappedId);
        matsimMappedId = matsimMappedId.concat(duplicateCount.toString());
        linkSegment.setExternalId(matsimMappedId);
        duplicateCount.increment();
      }else {
        alreadyUsedExternalMatsimIds.put(matsimMappedId, new LongAdder());
      }
    }
    return matsimMappedId;
  }

  public static String getMappedMatsimLinkId(
      MacroscopicLinkSegment linkSegment,
      final IdMapperType idMapperType,
      final NetworkIdMapper networkSegmentIdMapper,
      final Map<String, LongAdder> usedExternalMatsimIds) {

    String matsimMappedId = networkSegmentIdMapper.getMacroscopicLinkSegmentIdMapper().apply(linkSegment);
    if(idMapperType == IdMapperType.EXTERNAL_ID) {
      if(!usedExternalMatsimIds.containsKey(matsimMappedId)) {
        LOGGER.severe("No known MATSim link id mapped for PLANit link segment, this shouldn't happen");
      }
      usedExternalMatsimIds.get(matsimMappedId);
    }
    return matsimMappedId;
  }
}
