import {Function} from "@/model/settings/function/Function";
import {FunctionImport} from "@/model/settings/function/FunctionImport";

const emptyEntity: Function = {
  content: '',
  description: '',
  example: '',
  name: '',
  plugin: '',
  type: 'KEYWORD'
};
const emptyImportEntity: FunctionImport = {
  content: "", plugin: "", type: ""
};

const createHeaders = (i18n: any) => {
  return [
    {
      title: i18n.t('common.no'),
      key: 'id',
      sortable: 'custom'
    },
    {
      title: i18n.t('common.name'),
      key: 'name',
      ellipsis: true,
      tooltip: true
    },
    {
      title: i18n.t('common.description'),
      key: 'description',
      ellipsis: true,
      tooltip: true
    },
    {
      title: i18n.t('common.plugin'),
      key: 'plugin',
      slot: 'plugin',
      ellipsis: true,
      tooltip: true
    },
    {
      title: i18n.t('common.type'),
      key: 'type',
      slot: 'type',
      ellipsis: true,
      tooltip: true
    },
    {
      title: i18n.t('common.createTime'),
      key: 'createTime',
      ellipsis: true,
      tooltip: true
    },
    {
      title: i18n.t('common.updateTime'),
      key: 'updateTime',
      ellipsis: true,
      tooltip: true
    },
    {
      title: i18n.t('common.action'),
      slot: 'action',
      key: 'action',
      width: 150,
      align: 'center'
    }
  ];
};

const createDefaultType = (i18n: any) => [
  {label: i18n.t('common.keyword'), value: 'KEYWORD'},
  {label: i18n.t('common.operator'), value: 'OPERATOR'},
  {label: i18n.t('common.function'), value: 'FUNCTION'}
];

export {
  createHeaders,
  createDefaultType,
  emptyEntity,
  emptyImportEntity
}
