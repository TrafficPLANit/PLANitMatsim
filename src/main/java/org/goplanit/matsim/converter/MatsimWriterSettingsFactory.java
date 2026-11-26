package org.goplanit.matsim.converter;

import java.util.List;

import org.goplanit.matsim.converter.demand.MatsimDiscreteDemandsWriterSettings;
import org.goplanit.matsim.converter.network.MatsimNetworkWriterSettings;
import org.goplanit.matsim.util.PlanitMatsimWriterSettings;
import org.goplanit.utils.exceptions.SettingsProblemsException;
import org.goplanit.utils.settings.SettingsSource;
import org.goplanit.utils.settings.SettingsYamlLoader;

/**
 * Makes the settings of each MATSim writer from YAML settings files and text
 * <p>
 * For example, intermodal writer settings made from a file, then a writer given them:
 * </p>
 * <pre>
 * var settings = MatsimWriterSettingsFactory.createIntermodalWriterSettingsFromYaml(
 *     List.of(SettingsSource.ofFile(Path.of("matsim_writer.yaml"))));
 * var writer = MatsimIntermodalWriterFactory.create(settings);
 * </pre>
 * <p>
 * It is the one place to find how MATSim writer settings are made from files. The sources follow
 * {@link PlanitMatsimWriterSettings#SCHEMA} and apply in order, a later source winning for a replacing key. Each writer
 * has a file of its own; the intermodal writer's file names the files of the writers it holds. Every problem in the
 * sources is reported together, see {@link SettingsProblemsException}. The settings made are ordinary settings, so
 * nothing that takes settings changes, and code changing them afterwards wins. Settings made in code keep using the
 * constructors of the settings and the writers' own factories.
 * </p>
 *
 * @author markr
 */
public final class MatsimWriterSettingsFactory {

  /** Not instantiable, everything is static */
  private MatsimWriterSettingsFactory() {
  }

  /**
   * Network writer settings made from YAML sources
   *
   * @param sources in the order they apply
   * @return the settings
   * @throws SettingsProblemsException holding every problem found in the sources
   */
  public static MatsimNetworkWriterSettings createNetworkWriterSettingsFromYaml(List<SettingsSource> sources) {
    return new SettingsYamlLoader(PlanitMatsimWriterSettings.SCHEMA, sources)
        .create(MatsimNetworkWriterSettings.class);
  }

  /**
   * Zoning writer settings made from YAML sources
   *
   * @param sources in the order they apply
   * @return the settings
   * @throws SettingsProblemsException holding every problem found in the sources
   */
  public static MatsimZoningWriterSettings createZoningWriterSettingsFromYaml(List<SettingsSource> sources) {
    return new SettingsYamlLoader(PlanitMatsimWriterSettings.SCHEMA, sources)
        .create(MatsimZoningWriterSettings.class);
  }

  /**
   * Public transport services writer settings made from YAML sources
   *
   * @param sources in the order they apply
   * @return the settings
   * @throws SettingsProblemsException holding every problem found in the sources
   */
  public static MatsimPtServicesWriterSettings createPtServicesWriterSettingsFromYaml(List<SettingsSource> sources) {
    return new SettingsYamlLoader(PlanitMatsimWriterSettings.SCHEMA, sources)
        .create(MatsimPtServicesWriterSettings.class);
  }

  /**
   * Discrete demands writer settings made from YAML sources
   *
   * @param sources in the order they apply
   * @return the settings
   * @throws SettingsProblemsException holding every problem found in the sources
   */
  public static MatsimDiscreteDemandsWriterSettings createDiscreteDemandsWriterSettingsFromYaml(
      List<SettingsSource> sources) {
    return new SettingsYamlLoader(PlanitMatsimWriterSettings.SCHEMA, sources)
        .create(MatsimDiscreteDemandsWriterSettings.class);
  }

  /**
   * Intermodal writer settings made from YAML sources, each writer it holds made from the file or files a source
   * names for it
   *
   * @param sources in the order they apply
   * @return the settings
   * @throws SettingsProblemsException holding every problem found in the sources and the files they name
   */
  public static MatsimIntermodalWriterSettings createIntermodalWriterSettingsFromYaml(List<SettingsSource> sources) {
    return new SettingsYamlLoader(PlanitMatsimWriterSettings.SCHEMA, sources)
        .create(MatsimIntermodalWriterSettings.class);
  }
}
