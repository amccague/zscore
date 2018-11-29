import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static java.util.stream.Collectors.toList;

public class Score {

    private static class OutputValues {
        private BigDecimal requestedAmount;
        private BigDecimal interestRate;
        private BigDecimal monthlyRepayment;
        private BigDecimal totalRepayment;
    }

    public static void main(String[]args) {
        String executable = args[0];
        int score = scoreSubmission(executable);
        System.out.println(String.format("Scoring executable at:%s", executable));
        System.out.println(String.format("FS_SCORE:%d%%", score));
    }

    private static int scoreSubmission(String executable) {
        try {
            List<Integer> scores = Arrays.asList(
                testExampleCase(executable),
                testAmountTooHigh(executable),
                testAmountTooLow(executable),
                testIncrements(executable),
                testMonthlyRateCalculation(executable),
                testBlendedInterestRates(executable),
                testCompoundInterest(executable));

            double totalScore = 0;
            for (int score : scores) {
                totalScore += score / (double)scores.size();
            }
            return (int) Math.ceil(totalScore);

        } catch (Exception e) {
            System.err.println("Unable to score submission");
            e.printStackTrace(System.err);
            return 0;
        }
    }

    private static Optional<OutputValues> getValuesForAmount(String executable, int amount) throws IOException, InterruptedException {
        Process process = new ProcessBuilder()
            .command(executable, "market.csv", Integer.toString(amount))
            .start();

        try (BufferedReader reader = new BufferedReader(
            new InputStreamReader(
                process.getInputStream(),
                StandardCharsets.UTF_8))) {

            process.waitFor();

            List<String> output = reader.lines()
                .map(line -> {
                    String[] tokens = line.split(" ");
                    return tokens[tokens.length - 1];
                })
                .map(lastToken -> lastToken.replaceAll("[^\\d.\\-]", ""))
                .collect(toList());

            process.destroy();

            if (output.size() != 4) {
                System.err.println(String.format("Unexpected output from submission, did not produce 4 output values: %s", output.toString()));
                return Optional.empty();
            }

            OutputValues outputValues = new OutputValues();
            try {
                outputValues.requestedAmount = new BigDecimal(output.get(0));
                outputValues.interestRate = new BigDecimal(output.get(1));
                outputValues.monthlyRepayment = new BigDecimal(output.get(2));
                outputValues.totalRepayment = new BigDecimal(output.get(3));
            } catch (Exception e) {
                System.out.println(String.format("Outputs are not parsable: %s", output.toString()));
                return Optional.empty();
            }

            return Optional.of(outputValues);
        }
    }

    private static int testExampleCase(String executable) throws IOException, InterruptedException {
        int maxScore = 100;

        int score = getValuesForAmount(executable, 1000)
            .map(output -> {
                int s = 0;

                if (output.requestedAmount.compareTo(BigDecimal.valueOf(1000)) == 0) {
                    s += 25;
                }
                if (output.interestRate.compareTo(BigDecimal.valueOf(7.0)) == 0) {
                    s += 25;
                }
                if (output.monthlyRepayment.compareTo(BigDecimal.valueOf(30.78)) == 0) {
                    s += 25;
                }

                if (output.totalRepayment.compareTo(BigDecimal.valueOf(1108.10)) == 0) {
                    s += 25;
                } else if (valuesAreEqualWithin(output.totalRepayment, BigDecimal.valueOf(1108.10), BigDecimal.valueOf(0.02))) {
                    s += 20;
                }

                return s;
            })
            .orElse(0);

        System.out.println(String.format("Example case: Score %d/%d", score, maxScore));

        return score;
    }

    private static int testAmountTooHigh(String executable) throws IOException, InterruptedException {
        int maxScore = 25;
        int amount = 15100;

        int score = getValuesForAmount(executable, amount)
            .map(output -> {
                System.out.println(String.format("Amount too high: No result should be produced if the amount (%d) is too high", amount));
                return 0;
            })
            .orElse(maxScore);

        System.out.println(String.format("Amount too high: Score %d/%d", score, maxScore));

        return score;
    }

    private static int testAmountTooLow(String executable) throws IOException, InterruptedException {
        int maxScore = 25;
        int amount = 900;

        int score = getValuesForAmount(executable, amount)
            .map(output -> {
                System.out.println(String.format("Amount too low: No result should be produced if the amount (%d) is too low", amount));
                return 0;
            })
            .orElse(maxScore);

        System.out.println(String.format("Amount too low: Score %d/%d", score, maxScore));

        return score;
    }

    private static int testIncrements(String executable) throws IOException, InterruptedException {
        int maxScore = 25;
        int amount = 1050;

        int score = getValuesForAmount(executable, amount)
            .map(output -> {
                System.out.println(String.format("Increments: No result should be produced if the amount (%d) is not an increment of 100", amount));
                return 0;
            })
            .orElse(maxScore);

        System.out.println(String.format("Increments: Score %d/%d", score, maxScore));

        return score;
    }

    private static int testMonthlyRateCalculation(String executable) throws IOException, InterruptedException {
        int maxScore = 100;

        int score = getValuesForAmount(executable, 1000)
            .map(output -> {
                if (output.monthlyRepayment.compareTo(BigDecimal.valueOf(30.78)) == 0) {
                    return 100;
                }

                if (valuesAreEqualWithin(output.monthlyRepayment, BigDecimal.valueOf(30.78), BigDecimal.valueOf(0.02))) {
                    System.out.println(String.format("Monthly rate calculation: Result has reduced precision; monthly payment: %s", output.monthlyRepayment));
                    return 80;
                }

                if (valuesAreEqualWithin(output.monthlyRepayment, BigDecimal.valueOf(30.88), BigDecimal.valueOf(0.02))) {
                    System.out.println(String.format("Monthly rate calculation: Candidate potentially divided annual rate by 12; monthly payment: %s", output.monthlyRepayment));
                    return 80;
                }

                return 0;
            })
            .orElse(0);

        System.out.println(String.format("Monthly rate calculation: Score %d/%d", score, maxScore));

        return score;
    }

    private static int testBlendedInterestRates(String executable) throws IOException, InterruptedException {
        int maxScore = 100;

        int score = getValuesForAmount(executable, 1200)
            .map(output -> {
                if (valuesAreEqualWithin(output.monthlyRepayment, BigDecimal.valueOf(36.96), BigDecimal.valueOf(0.02))) {
                    return 100;
                }

                if (valuesAreEqualWithin(output.monthlyRepayment, BigDecimal.valueOf(30.88), BigDecimal.valueOf(0.02))) {
                    System.out.println(String.format("Blended interest rates: Candidate appears to have evenly averaged the interest rates of the lenders; monthly payment: %s", output.monthlyRepayment));
                    return 20;
                }

                return 0;
            })
            .orElse(0);

        System.out.println(String.format("Blended interest rates: Score %d/%d", score, maxScore));

        return score;
    }

    private static int testCompoundInterest(String executable) throws IOException, InterruptedException {
        int maxScore = 100;

        int score = getValuesForAmount(executable, 1200)
            .map(output -> {
                if (valuesAreEqualWithin(output.totalRepayment, BigDecimal.valueOf(1108.10), BigDecimal.valueOf(0.02))) {
                    return 100;
                } else {
                    System.out.println(String.format("Compound interest: Failed to produce expected amortised (%s) total repayment: %s", 1108.10, output.totalRepayment));
                }

                if (output.totalRepayment.compareTo(BigDecimal.valueOf(1200)) > 0 && output.totalRepayment.compareTo(BigDecimal.valueOf(1300)) < 0) {
                    System.out.println(String.format("Compound interest: Candidate has potentially compounded the principle without an amortising schedule; total repayment: %s", output.totalRepayment));
                    return 25;
                }

                if (output.totalRepayment.compareTo(BigDecimal.valueOf(1300)) > 0) {
                    System.out.println(String.format("Compound interest: Candidate's total repayment is far too high; total repayment: %s", output.totalRepayment));
                    return 0;
                }

                return 0;
            })
            .orElse(0);

        System.out.println(String.format("Compound interest: Score %d/%d", score, maxScore));

        return score;
    }

    private static boolean valuesAreEqualWithin(BigDecimal a, BigDecimal b, BigDecimal error) {
        return a.subtract(b).abs().compareTo(error) <= 0;
    }
}
