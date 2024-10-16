import { Box, styled } from '@mui/material';
import { TranslationProvider } from './translationProviders';
import { ProviderDescription } from './ProviderDescription';

const StyledContainer = styled(Box)`
  border: 1px solid ${({ theme }) => theme.palette.tokens.border.soft};
  gap: 20px;
  display: grid;
  border-radius: 16px;
  background: ${({ theme }) => theme.palette.tokens.background['paper-2']};
  padding: 20px;
`;

const StyledServices = styled(Box)`
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(260px, 1fr));
`;

const StyledDescription = styled(Box)`
  background: ${({ theme }) => theme.palette.tokens.background['paper-3']};
  padding: 0px 16px;
  border-radius: 16px;
`;

type Props = {
  provider: TranslationProvider;
};

export const TranslationAgency = ({ provider }: Props) => {
  const url = provider.url ? new URL(provider.url) : undefined;
  return (
    <StyledContainer>
      <Box display="flex" justifyContent="space-between">
        <img src={provider.logo} />
        {url && (
          <a target="_blank" href={url.toString()} rel="noreferrer">
            {url.host}
          </a>
        )}
      </Box>
      {provider.services && (
        <StyledServices>
          {provider.services.map((item) => (
            <Box key={item}>{item}</Box>
          ))}
        </StyledServices>
      )}
      {provider.description && (
        <StyledDescription>
          <ProviderDescription description={provider.description} />
        </StyledDescription>
      )}
    </StyledContainer>
  );
};
