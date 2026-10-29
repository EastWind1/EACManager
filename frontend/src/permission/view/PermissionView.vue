<!-- 角色与权限配置 -->
<template>
  <v-container>
    <v-card rounded="lg">
      <template #subtitle>
        <div class="d-flex">
          <v-spacer></v-spacer>
          <v-btn :disabled="!canUpdate" color="primary" @click="addRole">
            <template #prepend>
              <v-icon :icon="mdiPlus"></v-icon>
            </template>
            新增角色
          </v-btn>
        </div>
      </template>
      <template #text>
        <v-data-table
          :headers="roleHeaders"
          :items="roles"
          :search="search"
          density="comfortable"
          hover
          item-value="code"
          no-data-text="暂无角色"
        >
          <template #[`item.code`]="{ item }">
            <v-chip color="primary" size="small" variant="tonal">{{ item.code }}</v-chip>
          </template>
          <template #[`item.status`]="{ item }">
            <v-chip :color="item.disabled ? 'error' : 'success'" size="small" variant="tonal">
              {{ item.disabled ? '禁用' : '启用' }}
            </v-chip>
          </template>
          <template #[`item.actions`]="{ item }">
            <div class="d-flex justify-end ga-1">
              <v-btn
                :disabled="!canUpdate || item.id === undefined"
                color="primary"
                size="small"
                variant="tonal"
                @click="openPermissionDialog(item)"
              >
                配置权限
              </v-btn>
              <v-btn
                :disabled="!canUpdate"
                :icon="mdiPencil"
                color="primary"
                size="small"
                variant="text"
                @click="editRole(item)"
              ></v-btn>
              <v-btn
                :disabled="!canUpdate || item.code === ADMIN_ROLE"
                :icon="mdiDeleteOutline"
                color="error"
                size="small"
                variant="text"
                @click="removeRole(item)"
              ></v-btn>
            </div>
          </template>
        </v-data-table>
      </template>
    </v-card>

    <!-- 单角色权限配置 -->
    <v-dialog v-model="permissionDialog" max-width="960" scrollable>
      <v-card rounded="lg">
        <template #subtitle>
          <v-toolbar color="surface" density="comfortable">
            <v-icon :icon="mdiKeyOutline" color="primary" class="ms-4 me-2"></v-icon>
            <v-toolbar-title class="text-h6">
              配置权限
              <span class="text-medium-emphasis">/ {{ editingRole?.name }}</span>
            </v-toolbar-title>
            <v-chip v-if="editingRole?.disabled" class="me-2" color="error" size="small"
                    variant="tonal">
              已禁用
            </v-chip>
            <v-spacer></v-spacer>
            <v-btn :icon="mdiClose" variant="text" @click="permissionDialog = false"></v-btn>
          </v-toolbar>
        </template>
        <template #text>
          <v-alert v-if="editingRole?.code===ADMIN_ROLE" type="info" class="mb-2">
            管理员必须保留权限查看与变更权限
          </v-alert>
          <v-row>
            <v-col v-for="module in modules" :key="module.code" cols="12" md="6">
              <v-card class="h-100" rounded="lg">
                <template #title>
                  <div class="d-flex align-center">
                    <v-icon :icon="mdiFolderAccountOutline" color="primary" class="me-2"></v-icon>
                    <span class="text-subtitle-1">{{ module.moduleName }}</span>
                    <v-spacer></v-spacer>
                    <v-checkbox
                      :model-value="getModuleSelectState(module) == 2"
                      :indeterminate="getModuleSelectState(module) == 1"
                      :disabled="loading || !hasSelectable(module)"
                      label="全选"
                      hide-details
                      @update:model-value="selectAll(module, $event)"
                    ></v-checkbox>
                  </div>
                </template>
                <template #text>
                  <v-checkbox
                    v-for="item in module.permissions"
                    :key="item.id"
                    v-model="selectedPermissionIds"
                    :disabled="isAdminGuard(item.code) || loading"
                    :label="item.name"
                    hide-details
                    :value="item.id"
                  >
                  </v-checkbox>
                </template>
              </v-card>
            </v-col>
          </v-row>
        </template>

        <template #actions>
          <v-spacer></v-spacer>
          <v-btn variant="text" @click="permissionDialog = false">取消</v-btn>
          <v-btn
            :disabled="loading"
            color="primary"
            variant="flat"
            @click="savePermissions"
          >
            保存
          </v-btn>
        </template>
      </v-card>
    </v-dialog>

    <!-- 角色编辑 -->
    <v-dialog v-model="roleDialog" max-width="520">
      <v-card rounded="lg">
        <template #subtitle>
          <v-toolbar color="surface" density="comfortable">
            <v-icon :icon="mdiAccountCogOutline" color="primary" class="ms-4 me-2"></v-icon>
            <v-toolbar-title class="text-h6">{{
                roleForm.id ? '编辑角色' : '新增角色'
              }}
            </v-toolbar-title>
            <v-spacer></v-spacer>
            <v-btn :icon="mdiClose" variant="text" @click="roleDialog = false"></v-btn>
          </v-toolbar>
        </template>
        <template #text>
          <v-form v-model="roleValid">
            <v-text-field
              v-model="roleForm.code"
              :disabled="!!roleForm.id"
              label="角色编码"
              placeholder="如 MANAGER"
              prepend-inner-icon="mdiIdentifier"
              :rules="[required]"
              variant="outlined"
            ></v-text-field>
            <v-text-field
              v-model="roleForm.name"
              label="角色名称"
              prepend-inner-icon="mdiAccountOutline"
              :rules="[required]"
              variant="outlined"
            ></v-text-field>
            <v-number-input
              v-model="roleForm.sortOrder"
              label="排序"
              :min="0"
              variant="outlined"
            ></v-number-input>
            <v-switch
              v-model="roleForm.disabled"
              :disabled="roleForm.code === ADMIN_ROLE"
              color="primary"
              label="禁用"
            ></v-switch>
          </v-form>
        </template>
        <template #actions>
          <v-spacer></v-spacer>
          <v-btn @click="roleDialog = false">取消</v-btn>
          <v-btn color="primary" @click="saveRole">保存</v-btn>
        </template>
      </v-card>
    </v-dialog>
  </v-container>
</template>

<script lang="ts" setup>
import {computed, onMounted, ref} from 'vue'
import {
  mdiAccountCogOutline,
  mdiClose,
  mdiDeleteOutline,
  mdiFolderAccountOutline,
  mdiKeyOutline,
  mdiPencil,
  mdiPlus
} from '@mdi/js'
import SecurityApi from '../api/SecurityApi'
import type {Permission as PermissionDTO, Role} from '../model/Permission'
import {Permission} from '../model/PermissionCode'
import {useUserStore} from '@/user/store/UserStore'
import {useUIStore} from '@/common/store/UIStore'
import {storeToRefs} from "pinia";

const userStore = useUserStore()
const uiStore = useUIStore()
const {success, confirm} = uiStore
const {loading} = storeToRefs(uiStore)
const ADMIN_ROLE = 'ADMIN'

const roleHeaders = [
  {title: '角色编码', key: 'code', sortable: true},
  {title: '角色名称', key: 'name', sortable: false},
  {title: '排序', key: 'sortOrder', sortable: true, align: 'end' as const},
  {title: '状态', key: 'status', sortable: false},
  {title: '操作', key: 'actions', sortable: false, align: 'end' as const},
]

const roles = ref<Role[]>([])
const permissionGroups = ref<Record<string, PermissionDTO[]>>({})
const search = ref('')

const permissionDialog = ref(false)
const editingRole = ref<Role>()
const selectedPermissionIds = ref<number[]>([])

const roleDialog = ref(false)
const roleValid = ref(true)
const roleForm = ref<Role>({
  code: '',
  name: '',
  sortOrder: 0,
  disabled: false,
})

const canUpdate = computed(() => userStore.hasPermission(Permission.PERMISSION_UPDATE))

const modules = computed(() =>
  Object.entries(permissionGroups.value)
    .map(([code, list]) => ({
      code,
      moduleName: list[0]?.moduleName ?? code,
      permissions: [...list].sort((a, b) => a.sortOrder - b.sortOrder),
    }))
    .sort(
      (a, b) =>
        (a.permissions[0]?.sortOrder ?? 0) - (b.permissions[0]?.sortOrder ?? 0) ||
        a.code.localeCompare(b.code),
    ),
)

const required = (value: string) => !!value || '必填'

function isAdminGuard(code: string) {
  return (
    editingRole.value?.code === ADMIN_ROLE &&
    (code === Permission.PERMISSION_QUERY || code === Permission.PERMISSION_UPDATE)
  )
}


async function loadData() {
  const [roleList, permissionList] = await Promise.all([
    SecurityApi.getRoles(),
    SecurityApi.getPermissions(),
  ])
  roles.value = roleList
  permissionGroups.value = permissionList
}

async function openPermissionDialog(role: Role) {
  if (role.id === undefined) {
    return
  }
  editingRole.value = role
  permissionDialog.value = true

  selectedPermissionIds.value = (await SecurityApi.getRolePermissions(role.id)).map(
    (permission) => permission.id,
  )

}

function hasSelectable(module: { permissions: PermissionDTO[] }) {
  return module.permissions.some((item) => !isAdminGuard(item.code))
}

function getModuleSelectState(module: { permissions: PermissionDTO[] }): number {
  const selectable = module.permissions.filter((item) => !isAdminGuard(item.code))
  if (selectable.length === 0) {
    return 2
  }
  const selectedCount = selectable.filter((item) => selectedPermissionIds.value.includes(item.id)).length
  if (selectedCount == 0) {
    return 0
  }
  if (selectedCount < selectable.length) {
    return 1
  }
  return 2
}

function selectAll(module: { permissions: PermissionDTO[] }, value: boolean | null) {
  if (value) {
    const selected = new Set(selectedPermissionIds.value)
    for (const item of module.permissions) {
      if (!isAdminGuard(item.code)) {
        selected.add(item.id)
      }
    }
    selectedPermissionIds.value = [...selected]
  } else {
    const ids = new Set(module.permissions.map((item) => item.id))
    const guardedIds = new Set(
      module.permissions.filter((item) => isAdminGuard(item.code)).map((item) => item.id),
    )
    selectedPermissionIds.value = selectedPermissionIds.value.filter(
      (id) => !ids.has(id) || guardedIds.has(id),
    )
  }
}

async function savePermissions() {
  if (editingRole.value?.id === undefined) {
    return
  }
  await SecurityApi.updateRolePermissions(editingRole.value.id, selectedPermissionIds.value)
  permissionDialog.value = false
  success('权限保存成功')

}

function addRole() {
  roleForm.value = {
    code: '',
    name: '',
    sortOrder: roles.value.length + 1,
    disabled: false,
  }
  roleValid.value = true
  roleDialog.value = true
}

function editRole(role: Role) {
  roleForm.value = {...role}
  roleValid.value = true
  roleDialog.value = true
}

async function saveRole() {
  if (!roleValid.value) {
    return
  }
  if (roleForm.value.id) {
    await SecurityApi.updateRole(roleForm.value)
  } else {
    await SecurityApi.createRole(roleForm.value)
  }
  roleDialog.value = false
  roles.value = await SecurityApi.getRoles()
  success('角色已保存')
}

async function removeRole(role: Role) {
  if (!role.id || role.code === ADMIN_ROLE) {
    return
  }
  if (!(await confirm('确认', `确定删除角色 ${role.name} 吗？`))) {
    return
  }
  await SecurityApi.deleteRole(role.id)
  roles.value = await SecurityApi.getRoles()
}

onMounted(loadData)
</script>
