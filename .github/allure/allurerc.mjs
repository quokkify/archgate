export default {
  name: "archgate",
  output: "./allure-report",
  historyPath: "./allure-history/history.jsonl",
  historyLimit: 20,
  plugins: {
    awesome: {
      options: {
        reportName: "archgate test report",
        singleFile: false,
        reportLanguage: "en",
        groupBy: ["parentSuite", "subSuite"],
      },
    },
  },
};
