import { useEffect } from 'react';
import { Redirect, matchPath, useHistory } from 'react-router-dom';
import { GlobalLoading } from 'tg.component/GlobalLoading';
import { LINKS } from 'tg.constants/links';
import {
  useGlobalActions,
  useGlobalContext,
} from 'tg.globalContext/GlobalContext';

const PATHS_TO_REMEMBER = [LINKS.PROJECT];

export const RedirectUnsignedUser: React.FC = ({ children }) => {
  const shouldRedirect = useGlobalContext((c) => !c.auth.allowPrivate);

  const history = useHistory();
  const currentLocation = history.location.pathname + history.location.search;
  const { saveAfterLoginLink } = useGlobalActions();

  useEffect(() => {
    const shouldRemember = PATHS_TO_REMEMBER.some((link) =>
      matchPath(currentLocation, {
        path: link.template,
        exact: false,
        strict: false,
      })
    );
    if (shouldRemember && shouldRedirect) {
      saveAfterLoginLink(currentLocation);
    }
  }, [shouldRedirect, currentLocation]);

  if (shouldRedirect) {
    return (
      <Redirect to={LINKS.LOGIN.build()}>
        <GlobalLoading />
      </Redirect>
    );
  } else {
    return <>{children}</>;
  }
};
