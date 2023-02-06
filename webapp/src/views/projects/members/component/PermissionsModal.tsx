import { useState } from 'react';
import { T } from '@tolgee/react';
import {
  Button,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
} from '@mui/material';

import {
  LanguagePermissions,
  PermissionModel,
  PermissionSettingsState,
} from 'tg.component/PermissionsSettings/types';
import LoadingButton from 'tg.component/common/form/LoadingButton';
import { PermissionsSettings } from 'tg.component/PermissionsSettings/PermissionsSettings';
import { useApiMutation } from 'tg.service/http/useQueryApi';
import { useProject } from 'tg.hooks/useProject';
import { useMessage } from 'tg.hooks/useSuccessMessage';
import { parseErrorResponse } from 'tg.fixtures/errorFIxtures';
import { getScopeLanguagePermission } from 'tg.ee/PermissionsAdvanced/hierarchyTools';

type Props = {
  onClose: () => void;
  user: { name?: string; id: number };
  permissions: PermissionModel;
};

export const PermissionsModal: React.FC<Props> = ({
  onClose,
  user,
  permissions,
}) => {
  const messages = useMessage();
  const project = useProject();

  const editRole = useApiMutation({
    url: '/v2/projects/{projectId}/users/{userId}/set-permissions/{permissionType}',
    method: 'put',
    invalidatePrefix: '/v2/projects/{projectId}/users',
  });

  const editScopes = useApiMutation({
    url: '/v2/projects/{projectId}/users/{userId}/set-permissions',
    method: 'put',
    invalidatePrefix: '/v2/projects/{projectId}/users',
  });

  const [settingsState, setSettingsState] = useState<
    PermissionSettingsState | undefined
  >(undefined);

  const changeRole = () => {
    if (!settingsState?.state.role) {
      return;
    }
    const permissionType = settingsState.state.role;
    editRole.mutate(
      {
        path: {
          userId: user?.id,
          permissionType,
          projectId: project.id,
        },
        query: {},
      },
      {
        onSuccess() {
          messages.success(<T>permissions_set_message</T>);
          onClose();
        },
        onError(e) {
          parseErrorResponse(e).forEach((err) => messages.error(<T>{err}</T>));
        },
      }
    );
  };

  const changeScopes = () => {
    if (!settingsState?.state.scopes) {
      return;
    }

    const languagePermissions: LanguagePermissions = {};

    settingsState.state.scopes
      .map(getScopeLanguagePermission)
      .forEach((property) => {
        if (property) {
          languagePermissions[property] = settingsState.state[property];
        }
      });

    editScopes.mutate(
      {
        path: { userId: user.id, projectId: project.id },
        query: {
          scopes: settingsState.state.scopes,
          ...languagePermissions,
        },
      },
      {
        onSuccess() {
          messages.success(<T>permissions_set_message</T>);
          onClose();
        },
        onError(e) {
          parseErrorResponse(e).forEach((err) => messages.error(<T>{err}</T>));
        },
      }
    );
  };

  return (
    <Dialog open={true} onClose={onClose} fullWidth>
      <DialogTitle>
        <T keyName="permission_dialog_title" params={{ name: user.name }} />
      </DialogTitle>
      <DialogContent sx={{ minHeight: 400 }}>
        <PermissionsSettings
          permissions={permissions}
          onChange={setSettingsState}
        />
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose}>
          <T keyName="permission_dialog_close" />
        </Button>
        <LoadingButton
          loading={editRole.isLoading}
          onClick={settingsState?.tab === 'basic' ? changeRole : changeScopes}
          color="primary"
          variant="contained"
        >
          <T keyName="permission_dialog_save" />
        </LoadingButton>
      </DialogActions>
    </Dialog>
  );
};
