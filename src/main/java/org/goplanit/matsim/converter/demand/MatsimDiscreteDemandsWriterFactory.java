package org.goplanit.matsim.converter.demand;

import org.goplanit.demands.Demands;
import org.goplanit.demands.discrete.DiscreteDemands;
import org.goplanit.matsim.converter.MatsimIntermodalWriter;
import org.goplanit.matsim.converter.MatsimIntermodalWriterSettings;
import org.goplanit.network.MacroscopicNetwork;
import org.goplanit.network.ServiceNetwork;
import org.goplanit.service.routed.RoutedServices;
import org.goplanit.utils.id.IdGroupingToken;
import org.goplanit.utils.locale.CountryNames;
import org.goplanit.zoning.Zoning;

/**
 * Factory for creating MatsimDiscreteDemandsWriters that write out MATSim plan files
 *
 * @author markr
 *
 */
public class MatsimDiscreteDemandsWriterFactory {

  /** Create a MatsimDiscreteDemandsWriter which persists PLANit discrete demands in MATSim plans format
   *
   * @param network to extract references from (if any)
   * @param zoning to extract references from (if any)
   * @return created MatsimDiscreteDemandsWriter
   */
  public static MatsimDiscreteDemandsWriter create(
      final MacroscopicNetwork network, final Zoning zoning) {
    return create(new MatsimDiscreteDemandsWriterSettings(),network, zoning);
  }

  /** Create a MatsimDiscreteDemandsWriter which persists PLANit discrete demands in MATSim plans format
   * 
   * @param outputDirectory to use
   * @param network to extract references from (if any)
   * @param zoning to extract references from (if any)
   * @return created MATSim writer
   */
  public static MatsimDiscreteDemandsWriter create(
      String outputDirectory, final MacroscopicNetwork network, final Zoning zoning) {
    var settings = new MatsimDiscreteDemandsWriterSettings(outputDirectory);
    return create(settings, network, zoning);
  }

  /** create  a MatsimDiscreteDemandsWriter  which persists PLANit discrete demands in MATSim plans format
   *
   * @param settings to use
   * @return created MATSim writer
   */
  public static MatsimDiscreteDemandsWriter create(
      MatsimDiscreteDemandsWriterSettings settings, final MacroscopicNetwork network, final Zoning zoning) {
    return new MatsimDiscreteDemandsWriter(settings, network, zoning);
  }

}
