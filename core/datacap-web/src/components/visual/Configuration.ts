import {Type} from "@/components/visual/Type";

export class Configuration
{
  headers: [] = [];
  columns: [] = [];
  type: Type = Type.TABLE;
  chartConfigure? = {
    xAxis: null,
    yAxis: null
  }
}
