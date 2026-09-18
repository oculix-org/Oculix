/*
 * Copyright (c) 2010-2020, sikuli.org, sikulix.com - MIT license
 */
package org.sikuli.support.ide;

import org.sikuli.basics.PreferencesUser;
import java.text.MessageFormat;
import java.util.*;
import org.sikuli.basics.Debug;

public class SikuliIDEI18N {
   static ResourceBundle i18nRB = null;
   static ResourceBundle i18nRB_en;
   static Locale curLocale;

   /**
    * Regions served by another region's translation: the bundle file is shared,
    * the locale the user sees and saves stays their own.
    */
   private static final Map<String, Locale> SHARED_BUNDLES = Map.of(
       "zh_HK", Locale.TRADITIONAL_CHINESE,
       "zh_MO", Locale.TRADITIONAL_CHINESE,
       "zh_SG", Locale.SIMPLIFIED_CHINESE,
       "zh_MY", Locale.SIMPLIFIED_CHINESE);

   /**
    * Bundle lookup that tries the shared region's bundle after the user's own
    * and before the bare language, so zh_HK finds IDE_zh_TW and zh_SG finds IDE_zh_CN.
    */
   static final ResourceBundle.Control SHARED_REGIONS = new ResourceBundle.Control() {
      @Override
      public List<Locale> getCandidateLocales(String baseName, Locale locale) {
         List<Locale> candidates = new ArrayList<>(super.getCandidateLocales(baseName, locale));
         Locale shared = SHARED_BUNDLES.get(locale.getLanguage() + "_" + locale.getCountry());
         if (shared != null && !candidates.contains(shared)) {
            int bareLanguage = candidates.indexOf(new Locale(locale.getLanguage()));
            candidates.add(bareLanguage < 0 ? candidates.size() - 1 : bareLanguage, shared);
         }
         return candidates;
      }
   };

   static {
      Locale locale_en = new Locale("en","US");
      i18nRB_en = ResourceBundle.getBundle("i18n/IDE",locale_en, SHARED_REGIONS);
      Locale locale = PreferencesUser.get().getLocale();
      curLocale = locale;
      if(!setLocale(locale)){
         locale = locale_en;
         PreferencesUser.get().setLocale(locale);
      }
   }

   public static boolean setLocale(Locale locale){
      try{
         i18nRB = ResourceBundle.getBundle("i18n/IDE",locale, SHARED_REGIONS);
      }
      catch(MissingResourceException e){
         Debug.error("SikuliIDEI18N: no locale for " + locale);
         return false;
      }
      return true;
   }

   public static String getLocaleShow() {
     String ret = curLocale.toString();
     if (i18nRB == null) ret += " (using en_US)";
     return ret;
   }

   public static String _I(String key, Object... args){
      String ret;
      if(i18nRB==null)
         ret = i18nRB_en.getString(key);
      else{
         try {
            ret = i18nRB.getString(key);
         } catch (MissingResourceException e) {
            ret = i18nRB_en.getString(key);
         }
      }
      if(args.length>0){
         MessageFormat formatter = new MessageFormat("");
         formatter.setLocale(curLocale);
         formatter.applyPattern(ret);
         ret = formatter.format(args);
      }
      return ret;
   }

}
