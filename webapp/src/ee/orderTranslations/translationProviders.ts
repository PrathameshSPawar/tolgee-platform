export type TranslationProviderType = {
  id: number;
  logo: string;
  url?: string;
  services?: string[];
  description?: string;
};

export const translationProviders = [
  {
    id: 1,
    logo: '/images/translationAgencies/alconost.svg',
    url: 'https://alconost.com',
    services: [
      'Localization Expertise',
      'Linguistic Testing',
      'Continuous Localization',
      'Localization Assistance',
      '24x7 Customer Support',
      'Translation within 3 working days',
      'Source Texts Review',
      'Dedicated Project Manager',
      'Price from €0.1/word',
    ],
    description: `
Seamless localization of software, apps, games, websites, and other materials into 120+ languages native-speaking professional linguists
 - our translators and localization managers work directly in the Tolgee platform
 - we perform manual linguistic quality assurance in order to ensure the highest quality localization 
 - 1500+ localization projects in portfolio
 - localization testing and proofreading on demand
    `,
  },
  {
    id: 2,
    logo: '/images/translationAgencies/rapidTranslate.svg',
    url: 'https://www.rapidtranslate.org',
    description: `
 - Certified Translation Services
 - Official Translation Services 
 - ATA Approved 
 - Same Day Delivery Options 
 - Notarized Translations Available
`,
  },
] satisfies TranslationProviderType[];
