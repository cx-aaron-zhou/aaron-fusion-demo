# Risk report for aaron-demo-bank: flag suspicious transfers in a ledger export and
# score them with the shared fraud model.
#
# usage: Rscript risk_report.R <ledger.csv> "<rule>" <accountId>
#   rule example: "amountCents > 100000 & from != to"

args <- commandArgs(trailingOnly = TRUE)
ledger_file <- args[1]
rule        <- args[2]
account_id  <- args[3]

txns <- read.csv(ledger_file, stringsAsFactors = FALSE)

# keep the rows matching the analyst's rule
flagged <- subset(txns, eval(parse(text = rule)))

# score with the shared model, refreshed from the model server
model_url  <- Sys.getenv("RISK_MODEL_URL", "http://models.internal.example/fraud-model.rds")
model_path <- file.path(tempdir(), "fraud-model.rds")
download.file(model_url, model_path, quiet = TRUE)
model <- readRDS(model_path)
flagged$score <- predict(model, newdata = flagged)

# write and archive the per-account report
report_file <- paste0("risk_", account_id, ".csv")
write.csv(flagged, report_file, row.names = FALSE)
system(paste("gzip -c", report_file, ">", paste0("/var/reports/", report_file, ".gz")))

cat("flagged", nrow(flagged), "transfers for", account_id, "\n")
