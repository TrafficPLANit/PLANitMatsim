package org.goplanit.matsim.xml;

/**
 * MATSIM network XML attributes used
 * 
 * @author markr
 *
 */
public class MatsimNetworkAttributes {

  /** origid */
  public static final String ORIGID = "origid";  
  
  /* node specific */

  /** z */
  public static final String Z = "z";
    
  /* link specific */

  /** from */
  public static final String FROM = "from";

  /** to */
  public static final String TO = "to";

  /** length */
  public static final String LENGTH = "length";

  /** nt category */
  public static final String NT_CATEGORY = "nt_category";

  /** nt type */
  public static final String NT_TYPE = "nt_type";

  /** freespeed */
  public static final String FREESPEED_METER_SECOND = "freespeed";

  /** capacity */
  public static final String CAPACITY_HOUR = "capacity";

  /** permlanes */
  public static final String PERMLANES = "permlanes";

  /** modes */
  public static final String MODES = "modes";

  /** disallowednextlinks */
  public static final String DISALLOWED_NEXT_LINKS = "disallowedNextLinks";
  public static final String DISALLOWED_NEXT_LINKS_CLASS_VALUE =
      "org.matsim.core.network.turnRestrictions.DisallowedNextLinks";
  
}
